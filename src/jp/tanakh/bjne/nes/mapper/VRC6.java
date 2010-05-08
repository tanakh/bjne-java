package jp.tanakh.bjne.nes.mapper;

import java.util.LinkedList;
import java.util.Queue;

import jp.tanakh.bjne.nes.Mapper;
import jp.tanakh.bjne.nes.Nes;
import jp.tanakh.bjne.nes.Ppu;
import jp.tanakh.bjne.nes.Renderer.SoundInfo;

public class VRC6 implements Mapper {
	public VRC6(Nes n) {
		nes = n;
	}

	@Override
	public int mapperNo() {
		return 24;
	}

	@Override
	public void reset() {
		int romSize = nes.getRom().romSize();

		nes.getMbc().mapRom(0, 0);
		nes.getMbc().mapRom(1, 1);
		nes.getMbc().mapRom(2, (romSize - 1) * 2);
		nes.getMbc().mapRom(3, (romSize - 1) * 2 + 1);

		irqCount = 0;
		irqEnable = 0;
		irqLatch = 0;
		befClk = 0;

		sq[0] = new SqState();
		sq[1] = new SqState();
		saw = new SawState();
		
		writeQueue=new LinkedList<WriteDat>();
	}

	@Override
	public void write(short sadr, byte sdat) {
		int adr = sadr & 0xffff;
		int dat = sdat & 0xff;
		
		switch (adr & 0xF003) {
		case 0x8000: // Select 16K ROM bank at $8000
			nes.getMbc().mapRom(0, dat * 2);
			nes.getMbc().mapRom(1, dat * 2 + 1);
			break;
		case 0xC000: // Select 8K ROM bank at $C000
			nes.getMbc().mapRom(2, dat);
			break;

		case 0xD000: // Select 1K VROM bank at PPU $0000
			nes.getMbc().mapVrom(0, dat);
			break;
		case 0xD001: // Select 1K VROM bank at PPU $0400
			nes.getMbc().mapVrom(1, dat);
			break;
		case 0xD002: // Select 1K VROM bank at PPU $0800
			nes.getMbc().mapVrom(2, dat);
			break;
		case 0xD003: // Select 1K VROM bank at PPU $0C00
			nes.getMbc().mapVrom(3, dat);
			break;
		case 0xE000: // Select 1K VROM bank at PPU $1000
			nes.getMbc().mapVrom(4, dat);
			break;
		case 0xE001: // Select 1K VROM bank at PPU $1400
			nes.getMbc().mapVrom(5, dat);
			break;
		case 0xE002: // Select 1K VROM bank at PPU $1800
			nes.getMbc().mapVrom(6, dat);
			break;
		case 0xE003: // Select 1K VROM bank at PPU $1C00
			nes.getMbc().mapVrom(7, dat);
			break;

		case 0xB003:
			switch ((dat >> 2) & 3) {
			case 0: // Horizontal mirroring
				nes.getPpu().setMirroring(Ppu.MirrorType.VERTICAL);
				break;
			case 1: // Vertical mirroring
				nes.getPpu().setMirroring(Ppu.MirrorType.HOLIZONTAL);
				break;
			case 2: // Mirror page from $2000
				nes.getPpu().setMirroring(0, 0, 0, 0);
				break;
			case 3: // Mirror page from $2400
				nes.getPpu().setMirroring(1, 1, 1, 1);
				break;
			}
			break;

		case 0xF000:
			irqLatch = dat;
			break;
		case 0xF001:
			irqEnable = dat & 3;
			if ((irqEnable & 2) != 0)
				irqCount = irqLatch;
			break;
		case 0xF002:
			if ((irqEnable & 1) != 0)
				irqEnable |= 2;
			else
				irqEnable &= 1;
			break;

		case 0x9000:
		case 0x9001:
		case 0x9002:
		case 0xA000:
		case 0xA001:
		case 0xA002:
		case 0xB000:
		case 0xB001:
		case 0xB002:
			writeQueue
					.add(new WriteDat(nes.getCpu().getMasterClock(), sadr, sdat));
			while (writeQueue.size() > 100) {
				WriteDat wd = writeQueue.remove();
				sndWrite(wd.adr, wd.dat);
			}
			break;
		}
	}

	private void sndWrite(short sadr, byte sdat) {
		int adr = sadr & 0xffff;
		int dat = sdat & 0xff;
		switch (adr & 0xF003) {
		case 0x9000:
		case 0xA000:
			sq[(adr >> 12) - 9].duty = ((dat >> 4) & 7);
			sq[(adr >> 12) - 9].volume = dat & 0xF;
			sq[(adr >> 12) - 9].gate = (dat >> 7) != 0;
			break;
		case 0x9001:
		case 0xA001:
			sq[(adr >> 12) - 9].freq = (sq[(adr >> 12) - 9].freq & ~0xFF) | dat;
			break;
		case 0x9002:
		case 0xA002:
			sq[(adr >> 12) - 9].freq = (sq[(adr >> 12) - 9].freq & 0xFF)
					| ((dat & 0xF) << 8);
			sq[(adr >> 12) - 9].enable = (dat >> 7) != 0;
			break;

		case 0xB000:
			saw.phase = dat & 0x3F;
			break;
		case 0xB001:
			saw.freq = (saw.freq & ~0xFF) | dat;
			break;
		case 0xB002:
			saw.freq = (saw.freq & 0xFF) | ((dat & 0xF) << 8);
			saw.enable = (dat >> 7) != 0;
			break;
		}
	}

	@Override
	public void hblank(int line) {
		if ((irqEnable & 2) != 0) {
			if (irqCount >= 0xFF) {
				nes.getCpu().setIrq(true);
				irqCount = irqLatch;
			} else
				irqCount++;
		}
	}

	@Override
	public void audio(SoundInfo info) {
		double cpuClk = nes.getCpu().getFrequency();
		double sampleClk = cpuClk / info.freq;
		long curClk = nes.getCpu().getMasterClock();
		int span = info.bps / 8 * info.ch;

		for (int i = 0; i < info.sample; i++) {
			long cur = (curClk - befClk) * i / info.sample + befClk;
			while (!writeQueue.isEmpty() && cur >= writeQueue.peek().clk) {
				WriteDat wd = writeQueue.remove();
				sndWrite(wd.adr, wd.dat);
			}

			double d = (sqProduce(sq[0], sampleClk)
					+ sqProduce(sq[1], sampleClk) + sawProduce(sampleClk)) / 32.0;

			if (info.bps == 16) {
				short v = (short) (d * 8000);
				info.buf[i * span + 0] = (byte) (v & 0xff);
				info.buf[i * span + 1] = (byte) (v >> 8);
				if (info.ch == 2) {
					info.buf[i * span + 2] = (byte) (v & 0xff);
					info.buf[i * span + 3] = (byte) (v >> 8);
				}
			} else if (info.bps == 8) {
				info.buf[i * span + 0] = (byte) (d * 30);
				if (info.ch == 2) {
					info.buf[i * span + 1] = (byte) (d * 30);
				}
			}
		}

		befClk = curClk;
	}

	private int irqLatch, irqCount, irqEnable;

	private class SqState {
		int duty, volume;
		boolean gate;
		int freq;
		boolean enable;

		int step;
		double clk;
	}

	private SqState[] sq = new SqState[2];

	class SawState {
		int phase;
		int freq;
		boolean enable;

		int step;
		double clk;
	}

	private SawState saw;

	private int sqProduce(SqState sq, double clk) {
		if (!sq.enable)
			return 0;
		if (sq.gate)
			return sq.volume;
		sq.clk += clk;
		int adv = (int) (sq.clk / (sq.freq + 1));
		sq.clk -= (sq.freq + 1) * adv;
		sq.step = ((sq.step + adv) % 16);
		return ((sq.step > sq.duty) ? 1 : 0) * sq.volume;
	}

	private int sawProduce(double clk) {
		if (!saw.enable)
			return 0;
		saw.clk += clk;
		int adv = (int) (saw.clk / (saw.freq + 1));
		saw.clk -= (saw.freq + 1) * adv;
		saw.step = ((saw.step + adv) % 7);
		return ((saw.step * saw.phase) >> 3) & 0x1f;
	}

	private class WriteDat {
		WriteDat(long c, short a, byte d) {
			clk = c;
			adr = a;
			dat = d;
		}

		long clk;
		short adr;
		byte dat;
	};

	private Queue<WriteDat> writeQueue;

	private long befClk;

	private Nes nes;
}
