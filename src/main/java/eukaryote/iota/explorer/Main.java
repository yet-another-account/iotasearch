package eukaryote.iota.explorer;

import java.io.IOException;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import jota.utils.Converter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
	public static void main(String[] args) throws Exception {
		Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);

		int port = 80;
		
		log.info("Starting on port {}", port);
		
		Webserver ws = new Webserver(port);
	}
}
