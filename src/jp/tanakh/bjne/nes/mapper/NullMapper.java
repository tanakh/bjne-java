package jp.tanakh.bjne.nes.mapper;

import jp.tanakh.bjne.nes.MapperAdapter;
import jp.tanakh.bjne.nes.Nes;

public class NullMapper extends MapperAdapter {
	public NullMapper(Nes n){
	}

	@Override
	public int mapperNo() {
		return 0;
	}
}
