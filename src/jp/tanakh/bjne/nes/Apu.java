package jp.tanakh.bjne.nes;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import jp.tanakh.bjne.nes.Renderer.SoundInfo;

public class Apu {
	public Apu(Nes n) {
		this.nes = n;
		reset();
	}

	public void reset() {
		ch = new ChState[4];
		ch[0] = new ChState();
		ch[1] = new ChState();
		ch[2] = new ChState();
		ch[3] = new ChState();

		sch = new ChState[4];
		sch[0] = new ChState();
		sch[1] = new ChState();
		sch[2] = new ChState();
		sch[3] = new ChState();

		dmc = new DmcState();

		ch[3].shiftRegister = 1;
		befClock = befSync = 0;
	}

	public byte read(short adr) {
		if (adr == 0x4015) {
			sync();
			return (byte) ((sch[0].length == 0 ? 0 : 1) | ((sch[1].length == 0 ? 0 : 1) << 1) | ((sch[2].length == 0 ? 0 : 1) << 2)
					| ((sch[3].length == 0 ? 0 : 1) << 3) | ((sdmc.enable ? 1 : 0) << 4) | ((sdmc.irq ? 1 : 0) << 7));
		}
		return (byte) 0xA0;
	}

	public void write(short adr, byte dat) {
		// delay writing data for generating sound
		writeQueue.add(new WriteDat(nes.getCpu().getMasterClock(), adr, dat));
		while (writeQueue.size() > 1000) {
			WriteDat wd = writeQueue.remove();
			doWrite(ch, dmc, wd.adr, wd.dat);
		}
		// process for status register
		sync();
		doWrite(sch, sdmc, adr, dat);
	}

	public void genAudio(SoundInfo info) {
		double cpuClock = nes.getCpu().getFrequency();
		double framerate = (cpuClock * 2 / 14915);

		long curClock = nes.getCpu().getMasterClock();
		int sample = info.sample;

		byte[] buf = info.buf;
		int span = info.ch * (info.bps / 8);

		double incClk = ((double) (curClock - befClock)) / sample; // executed
		// CPU
		// clocks
		// per
		// sample
		double sampleClk = cpuClock / info.freq; // CPU clocks per sample

		Arrays.fill(buf, (byte) 0x00);

		if (nes.getMapper() != null) // external APU
			nes.getMapper().audio(info);

		for (int i = 0; i < sample; i++) {
			long pos = (curClock - befClock) * i / sample + befClock;
			while (!writeQueue.isEmpty() && writeQueue.peek().clk <= pos) {
				WriteDat wd = writeQueue.remove();
				doWrite(ch, dmc, wd.adr, wd.dat);
			}

			double v = 0;

			for (int j = 0; j < 4; j++) {
				ChState cc = ch[j];

				boolean pause = false;
				if (!cc.enable)
					continue;
				if (cc.length == 0)
					pause = true;

				// length counter
				if (cc.lengthEnable) {
					double length_clk = cpuClock / 60.0;
					cc.lengthClk += incClk;
					while (cc.lengthClk > length_clk) {
						cc.lengthClk -= length_clk;
						if (cc.length > 0)
							cc.length--;
					}
				}
				// linear counter
				if (j == TRI) {
					if (cc.counterStart != 0)
						cc.linearCounter = cc.linearLatch;
					else {
						double linear_clk = cpuClock / 240.0;
						cc.linearClk += incClk;
						while (cc.linearClk > linear_clk) {
							cc.linearClk -= linear_clk;
							if (cc.linearCounter > 0)
								cc.linearCounter--;
						}
					}
					if (!cc.holdnote && cc.linearCounter != 0)
						cc.counterStart = 0;

					if (cc.linearCounter == 0)
						pause = true;
				}

				// エンベロープ
				int vol = 16;
				if (j != TRI) {
					if (cc.envelopeEnable) {
						double decay_clk = cpuClock / (240.0 / (cc.envelopeRate + 1));
						cc.envelopeClk += incClk;
						while (cc.envelopeClk > decay_clk) {
							cc.envelopeClk -= decay_clk;
							if (cc.volume > 0)
								cc.volume--;
							else {
								if (!cc.lengthEnable) // ループ
									cc.volume = 0xf;
								else
									cc.volume = 0;
							}
						}
					}
					vol = cc.volume;
				}

				// スウィープ
				if ((j == SQ1 || j == SQ2) && cc.sweepEnable && !cc.sweepPausing) {
					double sweep_clk = cpuClock / (120.0 / (cc.sweepRate + 1));
					cc.sweepClk += incClk;
					while (cc.sweepClk > sweep_clk) {
						cc.sweepClk -= sweep_clk;
						if (cc.sweepShift != 0 && cc.length != 0) {
							if (!cc.sweepMode) // increase
								cc.waveLength += cc.waveLength >> cc.sweepShift;
							else
								// decrease
								cc.waveLength += ~(cc.waveLength >> cc.sweepShift); // 1の補数
							if (cc.waveLength < 0x008)
								cc.sweepPausing = true;
							if ((cc.waveLength & ~0x7FF) != 0)
								cc.sweepPausing = true;
							cc.waveLength &= 0x7FF;
						}
					}
				}

				pause |= cc.sweepPausing;
				pause |= cc.waveLength == 0;
				if (pause)
					continue;

				// 波形生成
				double t = ((j == SQ1 || j == SQ2) ? sqProduce(cc, sampleClk) : (j == TRI) ? triProduce(cc, sampleClk) : (j == NOI) ? noiProduce(cc, sampleClk)
						: 0);

				v += t * vol / 16;
			}

			v += dmcProduce(sampleClk);

			if (info.bps == 8) {
				buf[i * span + 0] += (byte) (v * 30);
				if (info.ch == 2)
					buf[i * span + 1] += (byte) (v * 30);
			} else {
				{
					short b = (short) ((buf[i * span + 0] & 0xff) | (buf[i * span + 1] << 8));
					short w = (short) Math.min(32767, Math.max(-32767, b + v * 8000));
					buf[i * span + 0] = (byte) (w & 0xff);
					buf[i * span + 1] = (byte) (w >> 8);
				}
				if (info.ch == 2) {
					short b = (short) ((buf[i * span + 2] & 0xff) | (buf[i * span + 3] << 8));
					short w = (short) Math.min(32767, Math.max(-32767, b + v * 8000));
					buf[i * span + 2] = (byte) (w & 0xff);
					buf[i * span + 3] = (byte) (w >> 8);
				}
			}

		}
		befClock = curClock;

	}

	public void sync() {
		double cpuClock = nes.getCpu().getFrequency();
		long cur = nes.getCpu().getMasterClock();
		int adv_clock = (int) (cur - befSync);

		// update 4 channels
		for (int j = 0; j < 4; j++) {
			ChState cc = sch[j];
			// length counter
			if (cc.enable && cc.lengthEnable) {
				double length_clk = cpuClock / 60.0;
				cc.lengthClk += adv_clock;
				int dec = (int) (cc.lengthClk / length_clk);
				cc.lengthClk -= length_clk * dec;
				cc.length = Math.max(0, cc.length - dec);
			}
		}
		// update DMC
		if (sdmc.enable) {
			sdmc.clk += adv_clock;
			int dec = (int) (sdmc.clk / sdmc.waveLength);
			sdmc.clk -= dec * sdmc.waveLength;

			int rest = sdmc.shiftCount + sdmc.length * 8 - dec;
			if (rest <= 0) { // end playback
				if ((sdmc.playbackMode & 1) != 0) { // loop
					sdmc.length = rest / 8;
					while (sdmc.length < 0)
						sdmc.length += sdmc.lengthLatch;
					sdmc.shiftCount = 0;
				} else {
					sdmc.enable = false;
					if (sdmc.playbackMode == 2) { // IRQ occur
						sdmc.irq = true;
						nes.getCpu().setIrq(true);
					}
				}
			} else {
				sdmc.length = rest / 8;
				sdmc.shiftCount = rest % 8;
			}
		}

		befSync = cur;
	}

	private class ChState {
		boolean enable;

		int waveLength;

		boolean lengthEnable;
		int length;
		double lengthClk;

		int volume;
		int envelopeRate;
		boolean envelopeEnable;
		double envelopeClk;

		boolean sweepEnable;
		int sweepRate;
		boolean sweepMode;
		int sweepShift;
		double sweepClk;
		boolean sweepPausing;

		int duty;

		int linearLatch;
		int linearCounter;
		boolean holdnote;
		int counterStart;
		double linearClk;

		boolean randomType;

		int step;
		double stepClk;
		int shiftRegister;
	}

	private ChState[] ch = null;
	private ChState[] sch = null;

	private class DmcState {
		boolean enable;
		boolean irq;

		int playbackMode;
		int waveLength;
		double clk;

		int counter;
		int length;
		int lengthLatch;
		short adr;
		short adrLatch;
		int shiftReg;
		int shiftCount;
		int dacLsb;
	};

	private DmcState dmc = new DmcState();
	private DmcState sdmc = new DmcState();

	static final int SQ1 = 0;
	static final int SQ2 = 1;
	static final int TRI = 2;
	static final int NOI = 3;
	static final int DMC = 4;

	final static int convTable[] = { 0x002, 0x004, 0x008, 0x010, 0x020, 0x030, 0x040, 0x050, 0x065, 0x07F, 0x0BE, 0x0FE, 0x17D, 0x1FC, 0x3F9, 0x7F2, };

	final static int lengthTbl[] = { 0x05, 0x06, 0x0A, 0x0C, 0x14, 0x18, 0x28, 0x30, 0x50, 0x60, 0x1E, 0x24, 0x07, 0x08, 0x0E, 0x10, };

	final static int dacTable[] = { 0xD60, 0xBE0, 0xAA0, 0xA00, 0x8F0, 0x7F0, 0x710, 0x6B0, 0x5F0, 0x500, 0x470, 0x400, 0x350, 0x2A0, 0x240, 0x1B0, };

	void doWrite(ChState[] ch, DmcState dmc, short adr, byte bdat) {
		int cn = (adr & 0x1f) / 4;
		ChState cc = null;
		if (cn < 4)
			cc = ch[cn];

		int dat = bdat & 0xff;

		switch (adr) {
		case 0x4000:
		case 0x4004:
		case 0x400C:
			cc.envelopeEnable = (dat & 0x10) == 0;
			if (cc.envelopeEnable) {
				cc.volume = 0xf;
				cc.envelopeRate = dat & 0xf;
			} else
				cc.volume = dat & 0xf;
			cc.lengthEnable = (dat & 0x20) == 0;
			cc.duty = dat >> 6;
			cc.envelopeClk = 0;
			break;
		case 0x4008:
			cc.linearLatch = dat & 0x7f;
			cc.holdnote = (dat & 0x80) != 0;
			break;

		case 0x4001:
		case 0x4005:
			cc.sweepShift = dat & 7;
			cc.sweepMode = (dat & 0x8) != 0;
			cc.sweepRate = (dat >> 4) & 7;
			cc.sweepEnable = (dat & 0x80) != 0;
			cc.sweepClk = 0;
			cc.sweepPausing = false;
			break;
		case 0x4009:
		case 0x400D: // unused
			break;

		case 0x4002:
		case 0x4006:
		case 0x400A:
			cc.waveLength = (cc.waveLength & ~0xff) | dat;
			break;
		case 0x400E: {
			cc.waveLength = convTable[dat & 0xf] - 1;
			cc.randomType = (dat & 0x80) != 0;
			break;
		}
		case 0x4003:
		case 0x4007:
		case 0x400B:
		case 0x400F: {
			if (cn != 3)
				cc.waveLength = (cc.waveLength & 0xff) | ((dat & 0x7) << 8);
			if ((dat & 0x8) == 0)
				cc.length = lengthTbl[dat >> 4];
			else
				cc.length = (dat >> 4) == 0 ? 0x7f : (dat >> 4);
			if (cn == 2)
				cc.counterStart = 1;

			if (cc.envelopeEnable) {
				cc.volume = 0xf;
				cc.envelopeClk = 0;
			}
			break;
		}

		case 0x4010: {
			dmc.playbackMode = dat >> 6;
			dmc.waveLength = dacTable[dat & 0xf] / 8;
			if ((dat >> 7) == 0)
				dmc.irq = false;
			break;
		}
		case 0x4011:
			dmc.dacLsb = dat & 1;
			dmc.counter = (dat >> 1) & 0x3f;
			break;
		case 0x4012:
			dmc.adrLatch = (short) ((dat << 6) | 0xC000);
			break;
		case 0x4013:
			dmc.lengthLatch = (dat << 4) + 1;
			break;

		case 0x4015:
			ch[0].enable = (dat & 1) != 0;
			if (!ch[0].enable)
				ch[0].length = 0;
			ch[1].enable = (dat & 2) != 0;
			if (!ch[1].enable)
				ch[1].length = 0;
			ch[2].enable = (dat & 4) != 0;
			if (!ch[2].enable)
				ch[2].length = 0;
			ch[3].enable = (dat & 8) != 0;
			if (!ch[3].enable)
				ch[3].length = 0;

			if ((dat & 0x10) != 0) {
				if (!dmc.enable) {
					dmc.adr = dmc.adrLatch;
					dmc.length = dmc.lengthLatch;
					dmc.shiftCount = 0;
				}
				dmc.enable = true;
			} else
				dmc.enable = false;
			dmc.irq = false;
			break;
		}
	}

	final static int sqWav[][] = { { 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, { 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
			{ 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1 }, { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1 }, };

	double sqProduce(ChState cc, double clk) {
		cc.stepClk += clk;
		double ret = (0.5 - sqWav[cc.duty][cc.step]);
		double term = cc.waveLength + 1;
		if (cc.stepClk >= term) {
			int t = (int) (cc.stepClk / term);
			cc.stepClk -= term * t;
			cc.step = (cc.step + t) % 16;
		}
		return ret;
	}

	final static int triWav[] = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, };

	double triProduce(ChState cc, double clk) {
		cc.stepClk += clk;
		double ret = (triWav[cc.step] / 16.0 - 0.5);
		double term = cc.waveLength + 1;
		if (cc.stepClk >= term) {
			int t = (int) (cc.stepClk / term);
			cc.stepClk -= term * t;
			cc.step = (cc.step + t) % 32;
		}
		return ret;
	}

	double noiProduce(ChState cc, double clk) {
		cc.stepClk += clk;
		double ret = 0.5 - (cc.shiftRegister >> 14);
		double term = cc.waveLength + 1;

		while (cc.stepClk >= term) {
			cc.stepClk -= term;
			int t = cc.shiftRegister;
			if (cc.randomType)
				cc.shiftRegister = ((t << 1) | (((t >> 14) ^ (t >> 8)) & 1)) & 0x7fff;
			else
				cc.shiftRegister = ((t << 1) | (((t >> 14) ^ (t >> 13)) & 1)) & 0x7fff;
		}
		return ret;
	}

	double dmcProduce(double clk) {
		if (!dmc.enable)
			return ((((dmc.counter << 1) | dmc.dacLsb) - 64) / 32.0);

		dmc.clk += clk;
		while (dmc.clk > dmc.waveLength) {
			dmc.clk -= dmc.waveLength;
			if (dmc.shiftCount == 0) {
				if (dmc.length == 0) { // is end?
					if ((dmc.playbackMode & 1) != 0) { // loop mode
						dmc.adr = dmc.adrLatch;
						dmc.length = dmc.lengthLatch;
					} else {
						dmc.enable = false;
						if (dmc.playbackMode == 2) { // occur IRQ
							dmc.irq = true;
							// nes.getCpu().setIrq(true); //
							// actually, IRQ occurs at sync()
						}
						return ((((dmc.counter << 1) | dmc.dacLsb) - 64) / 32.0);
					}
				}
				dmc.shiftCount = 8;
				dmc.shiftReg = nes.getMbc().read(dmc.adr);
				if (dmc.adr == 0xFFFF)
					dmc.adr = (short) 0x8000;
				else
					dmc.adr++;
				dmc.length--;
			}

			int b = dmc.shiftReg & 1;
			if (b == 0 && dmc.counter != 0) // decrement
				dmc.counter--;
			if (b == 1 && dmc.counter != 0x3F)
				dmc.counter++;
			dmc.counter &= 0x3f;
			dmc.shiftCount--;
			dmc.shiftReg >>= 1;
		}
		return ((((dmc.counter << 1) | dmc.dacLsb) - 64) / 32.0);
	}

	private class WriteDat {
		WriteDat(long clk, short adr, byte dat) {
			this.clk = clk;
			this.adr = adr;
			this.dat = dat;
		}

		long clk;
		short adr;
		byte dat;
	};

	private Queue<WriteDat> writeQueue = new LinkedList<WriteDat>();
	private long befClock;
	private long befSync;

	private Nes nes;
}
