package jp.tanakh.bjne.nes;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MapperMaker {
	@SuppressWarnings("unchecked")
	public static Mapper makeMapper(int num, Nes n) {
		Class<?>[] classes;
		try {
			classes = getClasses("jp.tanakh.bjne.nes.mapper");
			if (classes == null)
				return null;

			for (Class<?> c : classes) {
				try {
					Class<Mapper> mc = (Class<Mapper>) c;
					Constructor<Mapper> ctor = mc.getConstructor(Nes.class);
					Mapper ret = ctor.newInstance(n);
					if (ret.mapperNo() == num)
						return ret;
				} catch (Exception e) {
				}
			}

		} catch (Exception e) {
			return null;
		}
		return null;
	}

	public static Class<?>[] getClasses(String pckgname)
			throws ClassNotFoundException, IOException {
		ClassLoader cld = Thread.currentThread().getContextClassLoader();
		if (cld == null)
			return null;
		String path = pckgname.replace('.', '/');
		URL resource = cld.getResource(path);
		if (resource == null)
			return null;

		ArrayList<Class<?>> classes = new ArrayList<Class<?>>();

		if (resource.getProtocol() == "jar") {
			JarURLConnection conn = (JarURLConnection) resource
					.openConnection();
			JarFile jar = conn.getJarFile();
			for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements();) {
				JarEntry entry = en.nextElement();
				String entryName = entry.getName();
				if (!entryName.matches(path + "/.*.class"))
					continue;
				String className = entryName.replaceAll("/", ".").replace(
						".class", "");
				classes.add(Class.forName(className));
			}
		} else {
			File directory = new File(resource.getFile());
			if (!directory.exists())
				return null;

			String[] files = directory.list();
			for (int i = 0; i < files.length; i++)
				if (files[i].endsWith(".class"))
					classes.add(Class.forName(pckgname + '.'
							+ files[i].replace(".class", "")));
		}

		Class<?>[] ret = new Class<?>[classes.size()];
		classes.toArray(ret);
		return ret;
	}
}
