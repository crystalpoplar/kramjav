package com.example.kramjav.service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

@Component
public class WindowsPcActivityDetector implements PcActivityDetector {

	private static final User32 USER32 = Native.load("user32", User32.class);
	private static final Kernel32 KERNEL32 = Native.load("kernel32", Kernel32.class);

	@Override
	public boolean hasRecentUserInput(Duration activityWindow) {
		if (activityWindow == null || activityWindow.isNegative() || activityWindow.isZero()) {
			throw new IllegalArgumentException("Activity window must be a positive duration.");
		}

		if (!isWindows()) {
			return false;
		}

		LastInputInfo lastInputInfo = new LastInputInfo();
		lastInputInfo.cbSize = lastInputInfo.size();
		boolean loaded = USER32.GetLastInputInfo(lastInputInfo);
		if (!loaded) {
			throw new IllegalStateException("Unable to read Windows last-input state.");
		}

		int now = KERNEL32.GetTickCount();
		long idleMillis = Integer.toUnsignedLong(now - lastInputInfo.dwTime);
		return idleMillis <= activityWindow.toMillis();
	}

	private boolean isWindows() {
		String osName = System.getProperty("os.name", "");
		return osName.toLowerCase(Locale.ROOT).contains("win");
	}

	private interface User32 extends StdCallLibrary {
		boolean GetLastInputInfo(LastInputInfo plii);
	}

	private interface Kernel32 extends StdCallLibrary {
		int GetTickCount();
	}

	public static final class LastInputInfo extends Structure {
		public int cbSize;
		public int dwTime;

		@Override
		protected List<String> getFieldOrder() {
			return List.of("cbSize", "dwTime");
		}
	}
}
