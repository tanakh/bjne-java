package jp.tanakh.bjne.nes.mapper;

import jp.tanakh.bjne.nes.MapperAdapter;
import jp.tanakh.bjne.nes.Nes;

public class CNROM extends MapperAdapter {
	public CNROM(Nes n) {
		nes = n;
		reset();
	}

	@Override
	public int mapperNo() {
		return 3;
	}

	@Override
	public void reset() {
		for (int i = 0; i < 4; i++)
			nes.getMbc().mapRom(i, i);
		for (int i = 0; i < 8; i++)
			nes.getMbc().mapVrom(i, i);
	}

	@Override
	public void write(short adr, byte dat) {
		for (int i = 0; i < 8; i++)
			nes.getMbc().mapVrom(i, (dat & 0xff) * 8 + i);
	}

	private Nes nes;
}
