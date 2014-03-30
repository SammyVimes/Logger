package com.danilov.logger;

public interface ILogger {

	void log(final String message, final String className);
	
	void log(final String tag, final String message, final String className);
	
	void error(final String message, final String className);
	
}
