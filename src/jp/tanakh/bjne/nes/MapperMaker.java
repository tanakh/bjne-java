package jp.tanakh.bjne.nes;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;

public class MapperMaker {
	@SuppressWarnings("unchecked")
	public static Mapper makeMapper(int num, Nes n) {
		Class<?>[] classes;
		try {
			classes = getClasses("jp.tanakh.bjne.nes.mapper");
			if (classes == null)
				return null;

			for (Class<?> c : classes) {
				boolean isMapper = false;
				for (Class<?> i : c.getInterfaces()) {
					if (i.getName() == "jp.tanakh.bjne.nes.Mapper")
						isMapper = true;
				}
				if (!isMapper)
					continue;

				Class<Mapper> mc = (Class<Mapper>) c;
				Constructor<Mapper> ctor = mc.getConstructor(Nes.class);
				Mapper ret = ctor.newInstance(n);
				if (ret.mapperNo() == num)
					return ret;
			}

		} catch (Exception e) {
			return null;
		}
		return null;
	}

	public static Class<?>[] getClasses(String pckgname)
			throws ClassNotFoundException {
		ClassLoader cld = Thread.currentThread().getContextClassLoader();
		if (cld == null)
			return null;
		String path = pckgname.replace('.', '/');
		URL resource = cld.getResource(path);
		if (resource == null)
			return null;
		File directory = new File(resource.getFile());
		if (!directory.exists())
			return null;

		String[] files = directory.list();
		ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
		for (int i = 0; i < files.length; i++)
			if (files[i].endsWith(".class"))
				classes.add(Class.forName(pckgname + '.'
						+ files[i].substring(0, files[i].length() - 6)));

		Class<?>[] ret = new Class<?>[classes.size()];
		classes.toArray(ret);
		return ret;
	}
}
