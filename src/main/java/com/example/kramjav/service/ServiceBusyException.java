package com.example.kramjav.service;

public class ServiceBusyException extends RuntimeException {

	public ServiceBusyException(String message) {
		super(message);
	}
}
