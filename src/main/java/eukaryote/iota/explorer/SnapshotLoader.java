package eukaryote.iota.explorer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

public class SnapshotLoader {
	Map<String, Long> snapshot = new HashMap<>(16384);
	public SnapshotLoader(File f) throws IOException {
		List<String> lines = FileUtils.readLines(f, Charset.forName("UTF-8"));
		for (String line : lines) {
			String[] p = line.split(":", 2);
			snapshot.put(p[0], Long.parseLong(p[1]));
		}
	}
	
	public long getPreSnapshot(String hash) {
		return snapshot.getOrDefault(hash, 0L);
	}
}
