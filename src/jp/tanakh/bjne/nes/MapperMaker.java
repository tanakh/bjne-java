package jp.tanakh.bjne.nes;

import jp.tanakh.bjne.nes.mapper.MMC3;
import jp.tanakh.bjne.nes.mapper.NullMapper;


public class MapperMaker {
	static Mapper makeMapper(int num, Nes n) {
		switch (num) {
		case 0:
			return new NullMapper();
		
		case 4:
			return new MMC3(n);

		default:
			return null;
		}
	}
}
