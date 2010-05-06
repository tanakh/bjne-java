package jp.tanakh.bjne.nes;

public class Apu {
	public Apu(Nes n) {
		this.nes = n;
	}

	public void reset() {
	}

	public byte read(short adr) {
		return 0;
	}

	public void write(short adr, byte dat) {

	}

	private Nes nes;
}
