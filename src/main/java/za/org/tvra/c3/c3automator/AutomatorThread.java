package za.org.tvra.c3.c3automator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.JTextArea;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Issue;

public class AutomatorThread extends Thread {

	private JTextArea logTarget;
	private boolean stop = false;
	private boolean running = false;
	
	private WebDriver driver = null;
	private String redmineHost = "http://projects.tableviewratepayers.org.za";
    private String apiAccessKey = "578ff7113f024085f3956ec9dbde60b76a8c08bd"; // C3Automator User
    private String projectKey = "issue-database";
    private Integer queryId = 13;
	private Integer maxUpdates = 200;
	private Boolean updateIfStatusUnchanged = false;
	
	public AutomatorThread(JTextArea logTarget) {
		this.logTarget = logTarget;
	}

    public void listIssues() throws Exception {
        RedmineManager mgr = new RedmineManager(redmineHost, apiAccessKey);
		tryGetIssues(mgr);
    }

	private IssueStatus getStatusFromC3(String C3) {
		IssueStatus result = IssueStatus.UNKNOWN;
		
		String C3lower = C3.toLowerCase();
		if (C3lower.contains("notification in process")) result = IssueStatus.INPROGRESS;
		if (C3lower.contains("outstanding notification")) result = IssueStatus.INPROGRESS;
		if (C3lower.contains("outstanding task(s) exist(s)")) result = IssueStatus.INPROGRESS;
		if (C3lower.contains("notification printed")) result = IssueStatus.INPROGRESS;
		if (C3lower.contains("all tasks completed")) result = IssueStatus.C3UNCONFIRMED;
		
		return result;
	}
	
    private void tryGetIssues(RedmineManager mgr) throws Exception {
		log("Requesting list of issues updated more than 7 days ago");
        List<Issue> issues = mgr.getIssues(projectKey, queryId);
		int count = maxUpdates;
		if (count > issues.size()) count = issues.size();
		log("Received " + issues.size() + " issues to update. Updating " + count);
        for (Issue issue : issues) {
			String C3s = issue.getCustomField("C3 Reference Nr");
			log("Processing Issue #" + issue.getId() + " C3: " + C3s + " Subject:  " + issue.getSubject());
			
			if (stop) return;
			
			CustomField manualUpdateField = null;
			
			List<CustomField> fields = issue.getCustomFields();
			for (CustomField field : fields) {
				if (field.getId() == 10) {
					manualUpdateField = field;
				}
			}
			if (manualUpdateField == null) {
				manualUpdateField = new CustomField(10, "Requires manual update", "true");
				fields.add(manualUpdateField);
			}
			
			StringTokenizer st = new StringTokenizer(C3s, " \t\n\r\f\\/");
			int c3refcount = 0;
			while (st.hasMoreElements()) {
				if (stop) return;
				String c3ref = st.nextToken();
				String c3statustext = getC3Status(c3ref);
				IssueStatus c3status = getStatusFromC3(c3statustext);
				IssueStatus c4status = IssueStatus.findByCode(issue.getStatusId());
				log("C4: " + c4status.name() + " " + "C3: " + c3status.name());
				
				String notes = "C3Automater - status from C3: " + c3statustext;

				if (c3status != c4status) {
					issue.setStatusId(c3status.getCode());
					if (c3status == IssueStatus.C3UNCONFIRMED) {
						manualUpdateField.setValue("1");
						issue.setCustomFields(fields);
					}
				}
				
				if (c3status == IssueStatus.UNKNOWN) {
					if (!updateIfStatusUnchanged) {
						log("Status unknown, not logging.");
						continue;
					}
					manualUpdateField.setValue("1");
					issue.setCustomFields(fields);
					notes = notes + "\n\n" + "Unable to obtain status from the C3 text";
				}
				
				issue.setNotes(notes);
				
				if ((c3status == c4status) && (!updateIfStatusUnchanged)) {
					log("Status unchanged, not logging.");
					continue;
				}

				if (stop) return;

				mgr.update(issue);
				c3refcount++;
			}
			if (c3refcount > 0) --count; else log("Ignored, no updates");
			if (count <= 0) break;
			log("");
        }
    }
	
	public String getC3Status(String refNo) throws Exception {
		int retry = 10;
		while (retry -- > 0) {
			if (stop) return "";
			try {
				if (driver == null) createDriver();
				WebElement element = driver.findElement(By.id("notif_num"));
				element.sendKeys(refNo);
				element = driver.findElement(By.id("submitNotif"));
				element.click();
				log("Sleeping...");
				Thread.sleep(10000);
				element = driver.findElement(By.className("form_output"));
				String output = element.getText();
				return output;
			} catch (Exception e) {
				System.err.println("Exception: " + e);
				e.printStackTrace();
				log("Sleeping for 60 seconds on exception...");
				Thread.sleep(60000);
				createDriver();
			}
		}
		return "";
	}
	
	public void createDriver() throws Exception {
		try {
			if (driver != null) {
				log("An existing browser window already exists. Quitting it.");
				driver.quit();
				driver = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			log("Starting up a browser window.");
	        driver = new FirefoxDriver();
			log("Visiting CoCT service request website");
			driver.get("https://www.capetown.gov.za/en/ServiceRequests/Pages/ServicesRequestStatus.aspx");
			driver.switchTo().frame("MSOPageViewerWebPart_WebPartWPQ2");
			
		} catch (Exception e) {
			log("Error creating web browser driver: " + e.getMessage());
			if (driver != null) driver.quit();
			driver = null;
			throw e;
		}
	}
	
	private void log(String message) {
		String date = new SimpleDateFormat().format(new Date());
		System.out.println(date + ": " + message);
		logTarget.append(date + ": " + message + "\n");
	}
	
	private void logClear() {
		logTarget.setText(null);
		log("Log cleared");
	}
	
	@Override
	public void run() {
		
		synchronized (this) {
			running = true;
		}
		
		log("Starting. Using API key " + apiAccessKey);
		
		while (!stop) {
			
			log("Running");
			
			try {
				listIssues();
				log("Done. Closing browser.");
				driver.quit();
				int timer = 60;
				while (timer-- > 0) {
					log("Sleeing... " + timer + " minutes left");
					Thread.sleep(60);
				}
				logClear();
			} catch (Exception e) {
				log("Error: " + e.getMessage());
				if (driver != null) {
					driver.quit();
					driver = null;
				}
			}
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
			
		}
		
		synchronized (this) {
			running = false;
		}
		
		log("Stopped");
	}
	
	public void askToStop() {
		log("Stopping. Please wait for the system to shut down cleanly.");
		stop = true;
		interrupt();
	}

	@Override
	public synchronized void start() {
		
		if (apiAccessKey.length() < 10) {
			log("Invalid API access key");
			return;
		}
		
		synchronized (this) {
			if (running) {
				log("Already running");
				return;
			}
			stop = false;
		}
		super.start();
	}

	public String getApiAccessKey() {
		return apiAccessKey;
	}

	public void setApiAccessKey(String apiAccessKey) {
		this.apiAccessKey = apiAccessKey;
	}

	public Integer getMaxUpdates() {
		return maxUpdates;
	}

	public void setMaxUpdates(Integer maxUpdates) {
		this.maxUpdates = maxUpdates;
	}
	
}
