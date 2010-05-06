package jp.tanakh.bjne.nes;

public class Regs {
	public Regs(Nes n) {
		nes = n;
	}

	public void reset() {
		nmiEnable = false;
		ppuMaster = false; // unused
		spriteSize = false;
		bgPatAdr = spritePatAdr = false;
		ppuAdrIncr = false;
		nameTblAdr = 0;

		bgColor = 0;
		spriteVisible = bgVisible = false;
		spriteClip = bgClip = false;
		colorDisplay = false;

		isVBlank = false;
		sprite0Occur = spriteOver = false;
		vramWriteFlag = false;

		sprramAdr = 0;
		ppuAdrT = ppuAdrV = ppuAdrX = 0;
		ppuAdrToggle = false;
		ppuReadBuf = 0;

		joypadStrobe = false;
		joypadReadPos[0] = joypadReadPos[1] = 0;

		joypadSign[0] = 0x1;
		joypadSign[1] = 0x2;

		frameIrq = 0xFF;
	}

	public void setVBlank(boolean b, boolean nmi) {
		isVBlank = b;
		if (nmi && (!isVBlank || nmiEnable))
			nes.getCpu().setNmi(isVBlank);
		if (b)
			sprite0Occur = false;
	}

	public void startFrame() {
		if (bgVisible || spriteVisible)
			ppuAdrV = ppuAdrT;
	}

	public void startScanline() {
		if (bgVisible || spriteVisible)
			ppuAdrV = (short) ((ppuAdrV & 0xfbe0) | (ppuAdrT & 0x041f));
	}

	public void endScanline() {
		if (bgVisible || spriteVisible) {
			if (((ppuAdrV >> 12) & 7) == 7) {
				ppuAdrV &= ~0x7000;
				if (((ppuAdrV >> 5) & 0x1f) == 29)
					ppuAdrV = (short) ((ppuAdrV & ~0x03e0) ^ 0x800);
				else if (((ppuAdrV >> 5) & 0x1f) == 31)
					ppuAdrV &= ~0x03e0;
				else
					ppuAdrV += 0x20;
			} else
				ppuAdrV += 0x1000;
		}
	}

	public boolean drawEnabled() {
		return spriteVisible || bgVisible;
	}

	public void setInput(int[] dat) {
		for (int i = 0; i < 16; i++)
			padDat[i / 8][i % 8] = dat[i] != 0 ? true : false;
	}

	private static boolean _bit(int x, int n) {
		return ((x >> n) & 1) != 0;
	}

	private static int _set(boolean b, int n) {
		return ((b ? 1 : 0) << n);
	}

	public byte read(short adr) {
		// System.out.printf("[%04X] ->\n", adr & 0xffff);
		if ((adr & 0xffff) >= 0x4000) {
			switch (adr & 0xffff) {
			case 0x4016: // Joypad #1 (RW)
			case 0x4017: // Joypad #2 (RW)
			{
				int padNum = adr - 0x4016;
				int readPos = joypadReadPos[padNum];
				byte ret;
				if (readPos < 8) // パッドデータ
					ret = (byte) (padDat[padNum][readPos] ? 1 : 0);
				else if (readPos < 16) // Ignored
					ret = 0;
				else if (readPos < 20) // Signature
					ret = (byte) ((joypadSign[padNum] >> (readPos - 16)) & 1);
				else
					ret = 0;
				joypadReadPos[padNum]++;
				if (joypadReadPos[padNum] == 24)
					joypadReadPos[padNum] = 0;
				return ret;
			}
			case 0x4015:
				return (byte) (nes.getApu().read((short) 0x4015) | (((frameIrq & 0xC0) == 0) ? 0x40
						: 0));
			default:
				return nes.getApu().read(adr);
			}
		}

		switch (adr & 7) {
		case 0: // PPU Control Register #1 (W)
		case 1: // PPU Control Register #2 (W)
		case 3: // SPR-RAM Address Register (W)
		case 4: // SPR-RAM I/O Register (W)
		case 5: // VRAM Address Register #1 (W2)
		case 6: // VRAM Address Register #2 (W2)
			System.out.printf("read #%4x\n", adr);
			return 0;

		case 2: { // PPU Status Register (R)
			byte ret = (byte) (_set(isVBlank, 7) | _set(sprite0Occur, 6)
					| _set(spriteOver, 5) | _set(vramWriteFlag, 4));
			setVBlank(false, true);
			ppuAdrToggle = false;
			return ret;
		}
		case 7: { // VRAM I/O Register (RW)
			byte ret = ppuReadBuf;
			ppuReadBuf = read2007();
			return ret;
		}
		}

		return 0;
	}

	public void write(short adr, byte dat) {
		// System.out.printf("[%04X] <- %02X\n", adr & 0xffff, dat & 0xff);
		if ((adr & 0xffff) >= 0x4000) {
			switch (adr & 0xffff) {
			case 0x4014: // Sprite DMA Register (W)
			{
				byte[] sprram = nes.getPpu().getSpriteRam();
				for (int i = 0; i < 0x100; i++)
					sprram[i] = nes.getMbc().read((short) ((dat << 8) | i));
			}
				break;
			case 0x4016: // Joypad #1 (RW)
			{
				boolean newval = (dat & 1) != 0;
				if (joypadStrobe && !newval) // たち下りエッジでリセット
					joypadReadPos[0] = joypadReadPos[1] = 0;
				joypadStrobe = newval;
			}
				break;
			case 0x4017: // Joypad #2 (RW)
				frameIrq = dat;
				break;
			default:
				nes.getApu().write(adr, dat);
				break;
			}
			return;
		}

		switch (adr & 7) {
		case 0: // PPU Control Register #1 (W)
			nmiEnable = _bit(dat, 7);
			ppuMaster = _bit(dat, 6);
			spriteSize = _bit(dat, 5);
			bgPatAdr = _bit(dat, 4);
			spritePatAdr = _bit(dat, 3);
			ppuAdrIncr = _bit(dat, 2);
			// name_tbl_adr =dat&3;
			ppuAdrT = (short) ((ppuAdrT & 0xf3ff) | ((dat & 3) << 10));
			break;
		case 1: // PPU Control Register #2 (W)
			bgColor = dat >> 5;
			spriteVisible = _bit(dat, 4);
			bgVisible = _bit(dat, 3);
			spriteClip = _bit(dat, 2);
			bgClip = _bit(dat, 1);
			colorDisplay = _bit(dat, 0);
			break;
		case 2: // PPU Status Register (R)
			// どうするか…
			System.out.println("*** write to $2002");
			break;
		case 3: // SPR-RAM Address Register (W)
			sprramAdr = dat;
			break;
		case 4: // SPR-RAM I/O Register (W)
			nes.getPpu().getSpriteRam()[sprramAdr++] = dat;
			break;
		case 5: // VRAM Address Register #1 (W2)
			ppuAdrToggle = !ppuAdrToggle;
			if (ppuAdrToggle) {
				ppuAdrT = (short) ((ppuAdrT & 0xffe0) | ((dat & 0xff) >> 3));
				ppuAdrX = (short) (dat & 7);
			} else {
				ppuAdrT = (short) ((ppuAdrT & 0xfC1f) | (((dat & 0xff) >> 3) << 5));
				ppuAdrT = (short) ((ppuAdrT & 0x8fff) | ((dat & 7) << 12));
			}
			break;
		case 6: // VRAM Address Register #2 (W2)
			ppuAdrToggle = !ppuAdrToggle;
			if (ppuAdrToggle)
				ppuAdrT = (short) ((ppuAdrT & 0x00ff) | ((dat & 0x3f) << 8));
			else {
				ppuAdrT = (short) ((ppuAdrT & 0xff00) | (dat & 0xff));
				ppuAdrV = ppuAdrT;
			}
			break;
		case 7: // VRAM I/O Register (RW)
			write2007(dat);
			break;
		}
	}

	private byte read2007() {
		short adr = (short) (ppuAdrV & 0x3fff);
		ppuAdrV += ppuAdrIncr ? 32 : 1;
		if (adr < 0x2000)
			return nes.getMbc().readChrRom(adr);
		else if (adr < 0x3f00)
			return nes.getPpu().getNamePage()[(adr >> 10) & 3][adr & 0x3ff];
		else {
			if ((adr & 3) == 0)
				adr &= ~0x10;
			return nes.getPpu().getPalette()[adr & 0x1f];
		}
	}

	private void write2007(byte dat) {
		short adr = (short) (ppuAdrV & 0x3fff);
		ppuAdrV += ppuAdrIncr ? 32 : 1;
		if (adr < 0x2000) // CHR-ROM
			nes.getMbc().writeChrRom(adr, dat);
		else if (adr < 0x3f00) // name table
			nes.getPpu().getNamePage()[(adr >> 10) & 3][adr & 0x3ff] = dat;
		else { // palette
			if ((adr & 3) == 0)
				adr &= ~0x10; // mirroring
			nes.getPpu().getPalette()[adr & 0x1f] = (byte) (dat & 0x3f);
		}
	}

	public short getPpuAdrX() {
		return ppuAdrX;
	}

	public short getPpuAdrV() {
		return ppuAdrV;
	}

	public boolean getBgPatAdr() {
		return bgPatAdr;
	}

	public boolean getSpriteSize() {
		return spriteSize;
	}

	public boolean getSpritePatAdr() {
		return spritePatAdr;
	}

	public boolean getSpriteVisible() {
		return spriteVisible;
	}

	public boolean getBgVisible() {
		return bgVisible;
	}

	public int getFrameIrq() {
		return frameIrq;
	}

	public boolean getIsVBlank() {
		return isVBlank;
	}

	public void setSprite0Occur(boolean b) {
		this.sprite0Occur = b;
	}

	private boolean nmiEnable;
	private boolean ppuMaster;
	private boolean spriteSize;
	private boolean bgPatAdr, spritePatAdr;
	private boolean ppuAdrIncr;
	private int nameTblAdr;

	private int bgColor;
	private boolean spriteVisible, bgVisible;
	private boolean spriteClip, bgClip;
	private boolean colorDisplay;

	private boolean isVBlank;
	private boolean sprite0Occur, spriteOver;
	private boolean vramWriteFlag;

	private byte sprramAdr;
	private short ppuAdrT, ppuAdrV, ppuAdrX;
	private boolean ppuAdrToggle;
	private byte ppuReadBuf;

	private boolean joypadStrobe;
	private int[] joypadReadPos = new int[2];
	private int[] joypadSign = new int[2];
	private int frameIrq;

	private boolean[][] padDat = new boolean[2][8];

	private Nes nes;
}
