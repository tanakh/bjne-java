package jp.tanakh.bjne.nes.mapper;

import jp.tanakh.bjne.nes.Mapper;
import jp.tanakh.bjne.nes.Renderer.SoundInfo;

public class NullMapper implements Mapper {
	static int mapperNo(){
		return 0;
	}

	@Override
	public void audio(SoundInfo info) {
	}

	@Override
	public void hblank(int line) {
	}

	@Override
	public void write(short adr, byte dat) {
	}
}
