package jp.tanakh.bjne.mapper;

import jp.tanakh.bjne.Mapper;
import jp.tanakh.bjne.Renderer.SoundInfo;

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
