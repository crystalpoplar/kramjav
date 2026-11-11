package com.example.kramjav.service;

import java.time.Duration;

public interface PcActivityDetector {

	boolean hasRecentUserInput(Duration activityWindow);
}
