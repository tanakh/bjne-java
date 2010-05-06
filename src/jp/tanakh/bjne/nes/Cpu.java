package jp.tanakh.bjne.nes;

public class Cpu {
	public Cpu(Nes n) {
		nes = n;
		logging = false;
	}

	public void reset() {
		mbc = nes.getMbc();

		regPC = read16((short) 0xFFFC);
		regA = 0x00;
		regX = 0x00;
		regY = 0x00;
		regS = (byte) 0xFF;

		oprPC = 0;

		nFlag = vFlag = zFlag = cFlag = 0; // reset for Reproducibility
		bFlag = 1;
		dFlag = 0;
		iFlag = 1;

		rest = 0;
		mclock = 0;
		nmiLine = irqLine = resetLine = false;
	}

	private short imm() {
		return regPC++;
	}

	// TODO: penalty of carry
	private short abs() {
		regPC += 2;
		return read16(oprPC);
	}

	// private short abxi(){ regPC+=2; return
	// read16((short)(read16(oprPC)+(regX&0xff))); }
	private short abx() {
		regPC += 2;
		return (short) (read16(oprPC) + (regX & 0xff));
	}

	private short aby() {
		regPC += 2;
		return (short) (read16(oprPC) + (regY & 0xff));
	}

	private short absi() {
		regPC += 2;
		return read16(read16(oprPC));
	}

	private short zp() {
		return (short) (read8(regPC++) & 0xff);
	}

	private short zpxi() {
		return read16((short) ((read8(regPC++) + (regX & 0xff)) & 0xff));
	}

	private short zpx() {
		return (short) ((read8(regPC++) + (regX & 0xff)) & 0xff);
	}

	private short zpy() {
		return (short) ((read8(regPC++) + (regY & 0xff)) & 0xff);
	}

	// private short zpi(){ return read16((short)(read8(regPC++)&0xff)); }
	private short zpiy() {
		return (short) (read16((short) (read8(regPC++) & 0xff)) + (regY & 0xff));
	}

	private void push8(byte dat) {
		write8((short) (0x100 | ((regS--) & 0xff)), dat);
	}

	private byte pop8() {
		return read8((short) (0x100 | ((++regS) & 0xff)));
	}

	private void push16(short dat) {
		write16((short) (0x100 | ((regS - 1) & 0xff)), dat);
		regS -= 2;
	}

	private short pop16() {
		regS += 2;
		return read16((short) (0x100 | ((regS - 1) & 0xff)));
	}

	private byte bindFlags() {
		return (byte) ((nFlag << 7) | (vFlag << 6) | 0x20 | (bFlag << 4)
				| (dFlag << 3) | (iFlag << 2) | (zFlag << 1) | cFlag);
	}

	private void unbindFlags(byte dat) {
		nFlag = (byte) ((dat >> 7) & 1);
		vFlag = (byte) ((dat >> 6) & 1);
		bFlag = (byte) ((dat >> 4) & 1);
		dFlag = (byte) ((dat >> 3) & 1);
		iFlag = (byte) ((dat >> 2) & 1);
		zFlag = (byte) ((dat >> 1) & 1);
		cFlag = (byte) (dat & 1);
	}

	// ops

	// TODO : decimal support
	private void adc(int cycle, short adr) {
		byte s = read8(adr);
		int t = (regA & 0xff) + (s & 0xff) + (cFlag & 0xff);
		cFlag = (byte) (t >> 8);
		zFlag = (byte) ((t & 0xff) == 0 ? 1 : 0);
		nFlag = (byte) ((t >> 7) & 1);
		vFlag = (byte) ((((regA ^ s) & 0x80) == 0 && ((regA ^ t) & 0x80) != 0) ? 1
				: 0);
		regA = (byte) (t & 0xff);
		rest -= cycle;
	}

	// TODO : decimal support
	private void sbc(int cycle, short adr) {
		byte s = read8(adr);
		int t = (regA & 0xff) - (s & 0xff) - (cFlag != 0 ? 0 : 1);
		cFlag = (byte) ((t & 0xff00) == 0 ? 1 : 0);
		zFlag = (byte) ((t & 0xff) == 0 ? 1 : 0);
		nFlag = (byte) ((t >> 7) & 1);
		vFlag = (byte) ((((regA ^ s) & 0x80) != 0)
				&& (((regA ^ t) & 0x80) != 0) ? 1 : 0);
		regA = (byte) t;
		rest -= cycle;
	}

	private void cmp(int cycle, byte reg, short adr) {
		short t = (short) ((reg & 0xff) - (read8(adr) & 0xff));
		cFlag = (byte) ((t & 0xff00) == 0 ? 1 : 0);
		zFlag = (byte) ((t & 0xff) == 0 ? 1 : 0);
		nFlag = (byte) ((t >> 7) & 1);
		rest -= cycle;
	}

	private void and(int cycle, short adr) {
		regA &= read8(adr);
		nFlag = (byte) ((regA >> 7) & 1);
		zFlag = (byte) (regA == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void ora(int cycle, short adr) {
		regA |= read8(adr);
		nFlag = (byte) ((regA >> 7) & 1);
		zFlag = (byte) (regA == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void eor(int cycle, short adr) {
		regA ^= read8(adr);
		nFlag = (byte) ((regA >> 7) & 1);
		zFlag = (byte) (regA == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void bit(int cycle, short adr) {
		byte t = read8(adr);
		nFlag = (byte) ((t >> 7) & 1);
		vFlag = (byte) ((t >> 6) & 1);
		zFlag = (byte) ((regA & t) == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void loadA(int cycle, short adr) {
		regA = read8(adr);
		nFlag = (byte) ((regA >> 7) & 1);
		zFlag = (byte) (regA == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void loadX(int cycle, short adr) {
		regX = read8(adr);
		nFlag = (byte) ((regX >> 7) & 1);
		zFlag = (byte) (regX == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void loadY(int cycle, short adr) {
		regY = read8(adr);
		nFlag = (byte) ((regY >> 7) & 1);
		zFlag = (byte) (regY == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void store(int cycle, byte reg, short adr) {
		write8(adr, reg);
		rest -= cycle;
	}

	private void movA(int cycle, byte src) {
		regA = src;
		nFlag = (byte) ((src >> 7) & 1);
		zFlag = (byte) (src == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void movX(int cycle, byte src) {
		regX = src;
		nFlag = (byte) ((src >> 7) & 1);
		zFlag = (byte) (src == 0 ? 1 : 0);
		rest -= cycle;
	}

	private void movY(int cycle, byte src) {
		regY = src;
		nFlag = (byte) ((src >> 7) & 1);
		zFlag = (byte) (src == 0 ? 1 : 0);
		rest -= cycle;
	}

	private byte asli(byte arg) {
		cFlag = (byte) ((arg >> 7) & 1);
		arg <<= 1;
		nFlag = (byte) ((arg >> 7) & 1);
		zFlag = (byte) (arg == 0 ? 1 : 0);
		return arg;
	}

	private byte lsri(byte arg) {
		cFlag = (byte) (arg & 1);
		arg = (byte) ((arg & 0xff) >> 1);
		nFlag = (byte) ((arg >> 7) & 1);
		zFlag = (byte) (arg == 0 ? 1 : 0);
		return arg;
	}

	private byte roli(byte arg) {
		byte u = arg;
		arg = (byte) ((arg << 1) | cFlag);
		cFlag = (byte) ((u >> 7) & 1);
		nFlag = (byte) ((arg >> 7) & 1);
		zFlag = (byte) (arg == 0 ? 1 : 0);
		return arg;
	}

	private byte rori(byte arg) {
		byte u = arg;
		arg = (byte) (((arg & 0xff) >> 1) | (cFlag << 7));
		cFlag = (byte) (u & 1);
		nFlag = (byte) ((arg >> 7) & 1);
		zFlag = (byte) (arg == 0 ? 1 : 0);
		return arg;
	}

	private byte inci(byte arg) {
		arg++;
		nFlag = (byte) ((arg >> 7) & 1);
		zFlag = (byte) (arg == 0 ? 1 : 0);
		return arg;
	}

	private byte deci(byte arg) {
		arg--;
		nFlag = (byte) ((arg >> 7) & 1);
		zFlag = (byte) (arg == 0 ? 1 : 0);
		return arg;
	}

	private void asla(int cycle) {
		regA = asli(regA);
		rest -= cycle;
	}

	private void asl(int cycle, short adr) {
		write8(adr, asli(read8(adr)));
		rest -= cycle;
	}

	private void lsra(int cycle) {
		regA = lsri(regA);
		rest -= cycle;
	}

	private void lsr(int cycle, short adr) {
		write8(adr, lsri(read8(adr)));
		rest -= cycle;
	}

	private void rola(int cycle) {
		regA = roli(regA);
		rest -= cycle;
	}

	private void rol(int cycle, short adr) {
		write8(adr, roli(read8(adr)));
		rest -= cycle;
	}

	private void rora(int cycle) {
		regA = rori(regA);
		rest -= cycle;
	}

	private void ror(int cycle, short adr) {
		write8(adr, rori(read8(adr)));
		rest -= cycle;
	}

	private void incX(int cycle) {
		regX = inci(regX);
		rest -= cycle;
	}

	private void incY(int cycle) {
		regY = inci(regY);
		rest -= cycle;
	}

	private void inc(int cycle, short adr) {
		write8(adr, inci(read8(adr)));
		rest -= cycle;
	}

	private void decX(int cycle) {
		regX = deci(regX);
		rest -= cycle;
	}

	private void decY(int cycle) {
		regY = deci(regY);
		rest -= cycle;
	}

	private void dec(int cycle, short adr) {
		write8(adr, deci(read8(adr)));
		rest -= cycle;
	}

	private void bra(int cycle, boolean cond) {
		byte rel = read8(imm());
		rest -= cycle;
		if (cond) {
			rest -= (regPC & 0xff00) == ((regPC + rel) & 0xff00) ? 1 : 2;
			regPC += rel;
		}
	}

	public void exec(int clk) {
		rest += clk;
		mclock += clk;

		do {
			if (iFlag == 0) { // check IRQs
				if (resetLine) {
					execIrq(IrqType.RESET);
					resetLine = false;
				} else if (irqLine) {
					execIrq(IrqType.IRQ);
					irqLine = false;
				}
			}

			if (logging)
				log();

			byte opc = read8(regPC++);
			oprPC = regPC;

			switch (opc & 0xff) {
			/* ALU */
			case 0x69:
				adc(2, imm());
				break;
			case 0x65:
				adc(3, zp());
				break;
			case 0x75:
				adc(4, zpx());
				break;
			case 0x6D:
				adc(4, abs());
				break;
			case 0x7D:
				adc(4, abx());
				break;
			case 0x79:
				adc(4, aby());
				break;
			case 0x61:
				adc(6, zpxi());
				break;
			case 0x71:
				adc(5, zpiy());
				break;

			case 0xE9:
				sbc(2, imm());
				break;
			case 0xE5:
				sbc(3, zp());
				break;
			case 0xF5:
				sbc(4, zpx());
				break;
			case 0xED:
				sbc(4, abs());
				break;
			case 0xFD:
				sbc(4, abx());
				break;
			case 0xF9:
				sbc(4, aby());
				break;
			case 0xE1:
				sbc(6, zpxi());
				break;
			case 0xF1:
				sbc(5, zpiy());
				break;

			case 0xC9:
				cmp(2, regA, imm());
				break;
			case 0xC5:
				cmp(3, regA, zp());
				break;
			case 0xD5:
				cmp(4, regA, zpx());
				break;
			case 0xCD:
				cmp(4, regA, abs());
				break;
			case 0xDD:
				cmp(4, regA, abx());
				break;
			case 0xD9:
				cmp(4, regA, aby());
				break;
			case 0xC1:
				cmp(6, regA, zpxi());
				break;
			case 0xD1:
				cmp(5, regA, zpiy());
				break;

			case 0xE0:
				cmp(2, regX, imm());
				break;
			case 0xE4:
				cmp(2, regX, zp());
				break;
			case 0xEC:
				cmp(3, regX, abs());
				break;

			case 0xC0:
				cmp(2, regY, imm());
				break;
			case 0xC4:
				cmp(2, regY, zp());
				break;
			case 0xCC:
				cmp(3, regY, abs());
				break;

			case 0x29:
				and(2, imm());
				break;
			case 0x25:
				and(3, zp());
				break;
			case 0x35:
				and(4, zpx());
				break;
			case 0x2D:
				and(4, abs());
				break;
			case 0x3D:
				and(4, abx());
				break;
			case 0x39:
				and(4, aby());
				break;
			case 0x21:
				and(6, zpxi());
				break;
			case 0x31:
				and(5, zpiy());
				break;

			case 0x09:
				ora(2, imm());
				break;
			case 0x05:
				ora(3, zp());
				break;
			case 0x15:
				ora(4, zpx());
				break;
			case 0x0D:
				ora(4, abs());
				break;
			case 0x1D:
				ora(4, abx());
				break;
			case 0x19:
				ora(4, aby());
				break;
			case 0x01:
				ora(6, zpxi());
				break;
			case 0x11:
				ora(5, zpiy());
				break;

			case 0x49:
				eor(2, imm());
				break;
			case 0x45:
				eor(3, zp());
				break;
			case 0x55:
				eor(4, zpx());
				break;
			case 0x4D:
				eor(4, abs());
				break;
			case 0x5D:
				eor(4, abx());
				break;
			case 0x59:
				eor(4, aby());
				break;
			case 0x41:
				eor(6, zpxi());
				break;
			case 0x51:
				eor(5, zpiy());
				break;

			case 0x24:
				bit(3, zp());
				break;
			case 0x2C:
				bit(4, abs());
				break;

			/* laod / store */
			case 0xA9:
				loadA(2, imm());
				break;
			case 0xA5:
				loadA(3, zp());
				break;
			case 0xB5:
				loadA(4, zpx());
				break;
			case 0xAD:
				loadA(4, abs());
				break;
			case 0xBD:
				loadA(4, abx());
				break;
			case 0xB9:
				loadA(4, aby());
				break;
			case 0xA1:
				loadA(6, zpxi());
				break;
			case 0xB1:
				loadA(5, zpiy());
				break;

			case 0xA2:
				loadX(2, imm());
				break;
			case 0xA6:
				loadX(3, zp());
				break;
			case 0xB6:
				loadX(4, zpy());
				break;
			case 0xAE:
				loadX(4, abs());
				break;
			case 0xBE:
				loadX(4, aby());
				break;

			case 0xA0:
				loadY(2, imm());
				break;
			case 0xA4:
				loadY(3, zp());
				break;
			case 0xB4:
				loadY(4, zpx());
				break;
			case 0xAC:
				loadY(4, abs());
				break;
			case 0xBC:
				loadY(4, abx());
				break;

			case 0x85:
				store(3, regA, zp());
				break;
			case 0x95:
				store(4, regA, zpx());
				break;
			case 0x8D:
				store(4, regA, abs());
				break;
			case 0x9D:
				store(5, regA, abx());
				break;
			case 0x99:
				store(5, regA, aby());
				break;
			case 0x81:
				store(6, regA, zpxi());
				break;
			case 0x91:
				store(6, regA, zpiy());
				break;

			case 0x86:
				store(3, regX, zp());
				break;
			case 0x96:
				store(4, regX, zpy());
				break;
			case 0x8E:
				store(4, regX, abs());
				break;

			case 0x84:
				store(3, regY, zp());
				break;
			case 0x94:
				store(4, regY, zpx());
				break;
			case 0x8C:
				store(4, regY, abs());
				break;

			/* transfer */
			case 0xAA:
				movX(2, regA);
				break; // TAX
			case 0xA8:
				movY(2, regA);
				break; // TAY
			case 0x8A:
				movA(2, regX);
				break; // TXA
			case 0x98:
				movA(2, regY);
				break; // TYA
			case 0xBA:
				movX(2, regS);
				break; // TSX
			case 0x9A:
				regS = regX;
				rest -= 2;
				break; // TXS

			/* shift */
			case 0x0A:
				asla(2);
				break;
			case 0x06:
				asl(5, zp());
				break;
			case 0x16:
				asl(6, zpx());
				break;
			case 0x0E:
				asl(6, abs());
				break;
			case 0x1E:
				asl(7, abx());
				break;

			case 0x4A:
				lsra(2);
				break;
			case 0x46:
				lsr(5, zp());
				break;
			case 0x56:
				lsr(6, zpx());
				break;
			case 0x4E:
				lsr(6, abs());
				break;
			case 0x5E:
				lsr(7, abx());
				break;

			case 0x2A:
				rola(2);
				break;
			case 0x26:
				rol(5, zp());
				break;
			case 0x36:
				rol(6, zpx());
				break;
			case 0x2E:
				rol(6, abs());
				break;
			case 0x3E:
				rol(7, abx());
				break;

			case 0x6A:
				rora(2);
				break;
			case 0x66:
				ror(5, zp());
				break;
			case 0x76:
				ror(6, zpx());
				break;
			case 0x6E:
				ror(6, abs());
				break;
			case 0x7E:
				ror(7, abx());
				break;

			case 0xE6:
				inc(5, zp());
				break;
			case 0xF6:
				inc(6, zpx());
				break;
			case 0xEE:
				inc(6, abs());
				break;
			case 0xFE:
				inc(7, abx());
				break;
			case 0xE8:
				incX(2);
				break;
			case 0xC8:
				incY(2);
				break;

			case 0xC6:
				dec(5, zp());
				break;
			case 0xD6:
				dec(6, zpx());
				break;
			case 0xCE:
				dec(6, abs());
				break;
			case 0xDE:
				dec(7, abx());
				break;
			case 0xCA:
				decX(2);
				break;
			case 0x88:
				decY(2);
				break;

			/* branch */
			case 0x90:
				bra(2, cFlag == 0);
				break; // BCC
			case 0xB0:
				bra(2, cFlag != 0);
				break; // BCS
			case 0xD0:
				bra(2, zFlag == 0);
				break; // BNE
			case 0xF0:
				bra(2, zFlag != 0);
				break; // BEQ
			case 0x10:
				bra(2, nFlag == 0);
				break; // BPL
			case 0x30:
				bra(2, nFlag != 0);
				break; // BMI
			case 0x50:
				bra(2, vFlag == 0);
				break; // BVC
			case 0x70:
				bra(2, vFlag != 0);
				break; // BVS

			/* jump / call / return */
			case 0x4C:
				regPC = abs();
				rest -= 3;
				break; // JMP abs
			case 0x6C:
				regPC = absi();
				rest -= 5;
				break; // JMP (abs)

			case 0x20:
				push16((short) (regPC + 1));
				regPC = abs();
				rest -= 6;
				break; // JSR

			case 0x60:
				regPC = (short) (pop16() + 1);
				rest -= 6;
				break; // RTS
			case 0x40:
				unbindFlags(pop8());
				regPC = pop16();
				rest -= 6;
				break; // RTI

			/* flag */
			case 0x38:
				cFlag = 1;
				rest -= 2;
				break; // SEC
			case 0xF8:
				dFlag = 1;
				rest -= 2;
				break; // SED
			case 0x78:
				iFlag = 1;
				rest -= 2;
				break; // SEI

			case 0x18:
				cFlag = 0;
				rest -= 2;
				break; // CLC
			case 0xD8:
				dFlag = 0;
				rest -= 2;
				break; // CLD
			case 0x58:
				iFlag = 0;
				rest -= 2;
				break; // CLI
			case 0xB8:
				vFlag = 0;
				rest -= 2;
				break; // CLV

			/* stack */
			case 0x48:
				push8(regA);
				rest -= 3;
				break; // PHA
			case 0x08:
				push8(bindFlags());
				rest -= 3;
				break; // PHP
			case 0x68:
				regA = pop8();
				nFlag = (byte) ((regA >> 7) & 1);
				zFlag = (byte) (regA == 0 ? 1 : 0);
				rest -= 4;
				break; // PLA
			case 0x28:
				unbindFlags(pop8());
				rest -= 4;
				break; // PLP

			/* others */
			case 0x00: // BRK
				bFlag = 1;
				regPC++;
				execIrq(IrqType.IRQ);
				break;

			case 0xEA:
				rest -= 2;
				break; // NOP

			default:
				System.out.printf("undefined opcode: %02X\n", opc & 0xff);
				break;
			}
		} while (rest > 0);
	}

	public void setLogging(boolean b) {
		logging = b;
	}

	public void setNmi(boolean b) {
		// edge sensitive
		if (!nmiLine && b)
			execIrq(IrqType.NMI);
		nmiLine = b;
	}

	public void setIrq(boolean b) {
		irqLine = b;
	}

	public void setReset(boolean b) {
		resetLine = b;
	}

	public long getMasterClock() {
		return mclock - rest;
	}

	public double getFrequency() {
		return 3579545.0 / 2;
	}

	private byte read8(short adr) {
		return mbc.read(adr);
	}

	private short read16(short adr) {
		return (short) ((mbc.read(adr) & 0xff) | ((mbc.read((short) (adr + 1)) & 0xff) << 8));
	}

	private void write8(short adr, byte dat) {
		mbc.write(adr, (byte) dat);
	}

	private void write16(short adr, short dat) {
		mbc.write(adr, (byte) dat);
		mbc.write((short) (adr + 1), (byte) (dat >> 8));
	}

	private enum IrqType {
		NMI, IRQ, RESET,
	}

	private void execIrq(IrqType it) {
		if (logging)
			System.out.printf("%s occured!\n", it == IrqType.RESET ? "RESET"
					: it == IrqType.NMI ? "NMI" : "IRQ");

		short vect = (short) ((it == IrqType.RESET) ? 0xFFFC
				: (it == IrqType.NMI) ? 0xFFFA : (it == IrqType.IRQ) ? 0xFFFE
						: 0xFFFE);

		push16(regPC);
		push8(bindFlags());
		iFlag = 1;
		regPC = read16(vect);
		rest -= 7;
	}

	private int cnt = 0;

	private void log() {
		byte opc = read8(regPC);
		short opr = read16((short) (regPC + 1));
		String s = disasm(regPC, opc, opr);

		System.out
				.printf(
						"%06d: %04d: %04X %-13s : A:%02X X:%02X Y:%02X S:%02X P:%c%c%c%c%c%c%c\n",
						cnt++, rest, regPC & 0xffff, s, regA & 0xff,
						regX & 0xff, regY & 0xff, regS & 0xff, nFlag != 0 ? 'N'
								: 'n', vFlag != 0 ? 'V' : 'v', bFlag != 0 ? 'B'
								: 'b', dFlag != 0 ? 'D' : 'd', iFlag != 0 ? 'I'
								: 'i', zFlag != 0 ? 'Z' : 'z', cFlag != 0 ? 'C'
								: 'c');
	}

	static final String[] mne = { "BRK", "ORA", "UNK", "UNK", "UNK", "ORA",
			"ASL", "UNK", "PHP", "ORA", "ASL", "UNK", "UNK", "ORA", "ASL",
			"UNK", "BPL", "ORA", "UNK", "UNK", "UNK", "ORA", "ASL", "UNK",
			"CLC", "ORA", "UNK", "UNK", "UNK", "ORA", "ASL", "UNK", "JSR",
			"AND", "UNK", "UNK", "BIT", "AND", "ROL", "UNK", "PLP", "AND",
			"ROL", "UNK", "BIT", "AND", "ROL", "UNK", "BMI", "AND", "UNK",
			"UNK", "UNK", "AND", "ROL", "UNK", "SEC", "AND", "UNK", "UNK",
			"UNK", "AND", "ROL", "UNK", "RTI", "EOR", "UNK", "UNK", "UNK",
			"EOR", "LSR", "UNK", "PHA", "EOR", "LSR", "UNK", "JMP", "EOR",
			"LSR", "UNK", "BVC", "EOR", "UNK", "UNK", "UNK", "EOR", "LSR",
			"UNK", "CLI", "EOR", "UNK", "UNK", "UNK", "EOR", "LSR", "UNK",
			"RTS", "ADC", "UNK", "UNK", "UNK", "ADC", "ROR", "UNK", "PLA",
			"ADC", "ROR", "UNK", "JMP", "ADC", "ROR", "UNK", "BVS", "ADC",
			"UNK", "UNK", "UNK", "ADC", "ROR", "UNK", "SEI", "ADC", "UNK",
			"UNK", "UNK", "ADC", "ROR", "UNK", "UNK", "STA", "UNK", "UNK",
			"STY", "STA", "STX", "UNK", "DEY", "UNK", "TXA", "UNK", "STY",
			"STA", "STX", "UNK", "BCC", "STA", "UNK", "UNK", "STY", "STA",
			"STX", "UNK", "TYA", "STA", "TXS", "UNK", "UNK", "STA", "UNK",
			"UNK", "LDY", "LDA", "LDX", "UNK", "LDY", "LDA", "LDX", "UNK",
			"TAY", "LDA", "TAX", "UNK", "LDY", "LDA", "LDX", "UNK", "BCS",
			"LDA", "UNK", "UNK", "LDY", "LDA", "LDX", "UNK", "CLV", "LDA",
			"TSX", "UNK", "LDY", "LDA", "LDX", "UNK", "CPY", "CMP", "UNK",
			"UNK", "CPY", "CMP", "DEC", "UNK", "INY", "CMP", "DEX", "UNK",
			"CPY", "CMP", "DEC", "UNK", "BNE", "CMP", "UNK", "UNK", "UNK",
			"CMP", "DEC", "UNK", "CLD", "CMP", "UNK", "UNK", "UNK", "CMP",
			"DEC", "UNK", "CPX", "SBC", "UNK", "UNK", "CPX", "SBC", "INC",
			"UNK", "INX", "SBC", "NOP", "UNK", "CPX", "SBC", "INC", "UNK",
			"BEQ", "SBC", "UNK", "UNK", "UNK", "SBC", "INC", "UNK", "SED",
			"SBC", "UNK", "UNK", "UNK", "SBC", "INC", "UNK", };

	static final int adr[] = { 0, 12, 0, 0, 0, 8, 8, 0, 0, 1, 2, 0, 0, 3, 3, 0,
			14, 13, 0, 0, 0, 9, 9, 0, 0, 5, 0, 0, 0, 4, 4, 0, 3, 12, 0, 0, 8,
			8, 8, 0, 0, 1, 2, 0, 3, 3, 3, 0, 14, 13, 0, 0, 0, 9, 9, 0, 0, 5, 0,
			0, 0, 4, 4, 0, 0, 12, 0, 0, 0, 8, 8, 0, 0, 1, 2, 0, 3, 3, 3, 0, 14,
			13, 0, 0, 0, 9, 9, 0, 0, 5, 0, 0, 0, 4, 4, 0, 0, 12, 0, 0, 0, 8, 8,
			0, 0, 1, 2, 0, 7, 3, 3, 0, 14, 13, 0, 0, 0, 9, 9, 0, 0, 5, 0, 0, 0,
			4, 4, 0, 0, 12, 0, 0, 8, 8, 8, 0, 0, 0, 0, 0, 3, 3, 3, 0, 14, 13,
			0, 0, 9, 9, 10, 0, 0, 5, 0, 0, 0, 4, 0, 0, 1, 12, 1, 0, 8, 8, 8, 0,
			0, 1, 0, 0, 3, 3, 3, 0, 14, 13, 0, 0, 9, 9, 10, 0, 0, 5, 0, 0, 4,
			4, 5, 0, 1, 12, 0, 0, 8, 8, 8, 0, 0, 1, 0, 0, 3, 3, 3, 0, 14, 13,
			0, 0, 0, 9, 9, 0, 0, 5, 0, 0, 0, 4, 4, 0, 1, 12, 0, 0, 8, 8, 8, 0,
			0, 1, 0, 0, 3, 3, 3, 0, 14, 13, 0, 0, 0, 9, 9, 0, 0, 5, 0, 0, 0, 4,
			4, 0, };

	private String disasm(short pc, byte opc, short opr) {
		String op = mne[opc & 0xff];
		switch (adr[opc & 0xff]) {
		case 0: // Implied
			return op;
		case 1: // Immediate #$xx
			return String.format("%s #$%02X", op, opr & 0xff);
		case 2: // Accumerate
			return String.format("%s A", op);
		case 3: // Absolute $xxxx
			return String.format("%s $%04X", op, opr & 0xffff);
		case 4: // Absolute X $xxxx,X
			return String.format("%s $%04X,X", op, opr & 0xffff);
		case 5: // Absolute Y $xxxx,Y
			return String.format("%s $%04X,Y", op, opr & 0xffff);
		case 6: // Absolute X indirected ($xxxx,X)
			return String.format("%s ($%04X,X)", op, opr & 0xffff);
		case 7: // Absolute indirected
			return String.format("%s ($%04X)", op, opr & 0xffff);
		case 8: // Zero page $xx
			return String.format("%s $%02X", op, opr & 0xff);
		case 9: // Zero page indexed X $xx,X
			return String.format("%s $%02X,X", op, opr & 0xff);
		case 10: // Zero page indexed Y $xx,Y
			return String.format("%s $%02X,Y", op, opr & 0xff);
		case 11: // Zero page indirected ($xx)
			return String.format("%s ($%02X)", op, opr & 0xff);
		case 12: // Zero page indexed indirected ($xx,X)
			return String.format("%s ($%02X,X)", op, opr & 0xff);
		case 13: // Zero page indirected indexed ($xx),Y
			return String.format("%s ($%02X),Y", op, opr & 0xff);
		case 14: // Relative
			return String.format("%s $%04X", op,
					(pc + (byte) (opr & 0xff) + 2) & 0xffff);
		default:
			return "";
		}
	}

	private byte regA, regX, regY, regS;
	private short regPC;
	private byte cFlag, zFlag, iFlag, dFlag, bFlag, vFlag, nFlag;

	private short oprPC;

	private int rest;
	private long mclock;
	private boolean nmiLine, irqLine, resetLine;

	private boolean logging;

	private Nes nes;
	private Mbc mbc;
}
