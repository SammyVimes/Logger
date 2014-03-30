package com.danilov.logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("rawtypes")
public class Logger implements ILogger {
	
	private String ERROR_TAG = "ERROR";
	private String DEFAULT_TAG = "INFO";
	private SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("dd/MM hh:mm:ss");
	
	private Lock lock = new ReentrantLock();
	private Condition hasTasks = lock.newCondition();
	private Queue<LogTask> logTasks = new LinkedList<LogTask>();
	private String path;
	private String fileName;
	
	private LoggerRunnable loggerRunnable;
	
	public Logger(final String path, final String fileName) {
		this.path = path;
		this.fileName = fileName;
		loggerRunnable = new LoggerRunnable();
		Thread worker = new Thread(loggerRunnable);
		worker.start();
	}

	@Override
	public void log(final String message, final String className) {
		this.log(DEFAULT_TAG, message, className);
	}

	@Override
	public void error(final String message, final String className) {
		this.log(ERROR_TAG, message, className);
	}

	@Override
	public void log(final String tag, final String message, final String className) {
		if (!loggerRunnable.isRunning()) {
			System.out.println("Logger is stopping, no can do's-ville, baby-girl");
			return;
		}
		lock.lock();
		try {
			LogTask task = new LogTask(tag, message, className);
			logTasks.add(task);
			hasTasks.signal();
		} finally {
			lock.unlock();
		}
	}
	
	public void stopLogging() {
		loggerRunnable.setRunning(false);
	}
	
	private class LoggerRunnable implements Runnable {

		private boolean isRunning = true;
		private BufferedWriter writer;
		
		@Override
		public void run() {
			while (true) {
				boolean unlocked = false;
				lock.lock(); //locking only for getting task
				if (!isRunning) {
					if (logTasks.isEmpty()) {
						return; //exiting
					}
				}
				try {
					while(logTasks.isEmpty()) {
						try {
							hasTasks.await();
							if (!isRunning) {
								if (logTasks.isEmpty()) {
									return; //exiting
								}
							}
						} catch (InterruptedException e) {
							System.out.println("This should never have had happen");
							e.printStackTrace(); //funny if logger would fall and log it
						}
					}
					File file = new File(path + fileName);
					if (!file.exists()) {
						boolean successfullyCreated = false;
						try {
							File dir = new File(path);
							dir.mkdirs();
							file = new File(dir, fileName);
							file.createNewFile();
							successfullyCreated = true;
						} catch (IOException e) {
							System.out.println("Problems with file creation");
							e.printStackTrace();
						}
						if (!successfullyCreated) {
							continue;
						}
					}
					FileWriter fileWriter = null;
					try {
						fileWriter = new FileWriter(file, true);
					} catch (IOException e1) {
						System.out.println("Problems with file creation");
						e1.printStackTrace();
					}
					writer = new BufferedWriter(fileWriter);
					while (!logTasks.isEmpty()) {
						LogTask task = logTasks.poll();
						lock.unlock();
						try {
							writeToFile(task);
							writer.flush();
						} catch (IOException e) {
							System.out.println("Problems with file");
							e.printStackTrace();
						}
						lock.lock();
					}
					lock.unlock();
					unlocked = true;
				} finally {
					try {
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (!unlocked) {
						lock.unlock();
					}
				}
			}
		}
		
		public void setRunning(final boolean isRunning) {
			this.isRunning = isRunning;
		}
		
		public boolean isRunning() {
			return isRunning;
		}
		
		private void writeToFile(final LogTask task) throws IOException {
			String message = task.message;
			String tag = task.tag;
			Date d = task.date;
			String c = task.className;
			String out = "[" + DEFAULT_DATE_FORMAT.format(d) + "]" + "[" + c + "]["
						+ tag + "]: " + message + "\n";
			writer.write(out);
		}
		
	}

	private class LogTask {
		
		final String tag;
		final String message;
		final Date date;
		final String className;
		
		public LogTask(final String tag, final String message, final String className) {
			this.tag = tag;
			this.message = message;
			this.className = className;
			date = new Date();
		}
		
	}

}
