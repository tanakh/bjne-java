package jp.tanakh.bjne.nes;

import jp.tanakh.bjne.nes.mapper.NullMapper;


public class MapperMaker {
	static Mapper makeMapper(int num, Nes n) {
		switch (num) {
		case 0:
			return new NullMapper();

		default:
			return null;
		}
	}
}
