package za.org.tvra.c3.c3automator;

import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Issue;
import java.util.List;
import java.util.StringTokenizer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

public class App 
{
	private static WebDriver driver = null;
	private static String redmineHost = "http://projects.tableviewratepayers.org.za";
    private static String apiAccessKey = "578ff7113f024085f3956ec9dbde60b76a8c08bd"; // C3Automator User
    private static String projectKey = "issue-database";
    private static Integer queryId = 13;
	private static Integer maxUpdates = 200;
	private static Boolean updateIfStatusUnchanged = false;

    public static void listIssues() throws Exception {
        RedmineManager mgr = new RedmineManager(redmineHost, apiAccessKey);
		tryGetIssues(mgr);
    }

	private static IssueStatus getStatusFromC3(String C3) {
		IssueStatus result = IssueStatus.UNKNOWN;
		
		String C3lower = C3.toLowerCase();
		if (C3lower.contains("notification in process")) result = IssueStatus.INPROGRESS;
		if (C3lower.contains("outstanding notification")) result = IssueStatus.INPROGRESS;
		if (C3lower.contains("outstanding task(s) exist(s)")) result = IssueStatus.INPROGRESS;
		if (C3lower.contains("notification printed")) result = IssueStatus.INPROGRESS;
		if (C3lower.contains("all tasks completed")) result = IssueStatus.C3UNCONFIRMED;
		
		return result;
	}
	
    private static void tryGetIssues(RedmineManager mgr) throws Exception {
		System.out.println("Requesting list of issues updated more than 7 days ago");
        List<Issue> issues = mgr.getIssues(projectKey, queryId);
		int count = maxUpdates;
		if (count > issues.size()) count = issues.size();
		System.out.println("Received " + issues.size() + " issues to update. Updating " + count);
        for (Issue issue : issues) {
			String C3s = issue.getCustomField("C3 Reference Nr");
			System.out.println("Processing Issue #" + issue.getId() + " C3: " + C3s + " Subject:  " + issue.getSubject());
			
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
				String c3ref = st.nextToken();
				String c3statustext = getC3Status(c3ref);
				IssueStatus c3status = getStatusFromC3(c3statustext);
				IssueStatus c4status = IssueStatus.findByCode(issue.getStatusId());
				System.out.println("C4: " + c4status.name() + " " + "C3: " + c3status.name());
				
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
						System.out.println("Status unknown, not logging.");
						continue;
					}
					manualUpdateField.setValue("1");
					issue.setCustomFields(fields);
					notes = notes + "\n\n" + "Unable to obtain status from the C3 text";
				}
				
				issue.setNotes(notes);
				
				if ((c3status == c4status) && (!updateIfStatusUnchanged)) {
					System.out.println("Status unchanged, not logging.");
					continue;
				}
				
				mgr.update(issue);
				c3refcount++;
			}
			if (c3refcount > 0) --count; else System.out.println("Ignored, no updates");
			if (count <= 0) break;
			System.out.println("");
        }
    }
	
	public static String getC3Status(String refNo) throws Exception {
		int retry = 10;
		while (retry -- > 0) {
			try {
				WebElement element = driver.findElement(By.id("notif_num"));
				element.sendKeys(refNo);
				element = driver.findElement(By.id("submitNotif"));
				element.click();
				System.out.println("Sleeping...");
				Thread.sleep(10000);
				element = driver.findElement(By.className("form_output"));
				String output = element.getText();
				return output;
			} catch (Exception e) {
				System.err.println("Exception: " + e);
				e.printStackTrace();
				System.out.println("Sleeping on exception...");
				Thread.sleep(60000);
				driver = createDriver();
			}
		}
		return "";
	}
	
    public static void main( String[] args ) throws Exception
    {
//        WebDriver driver = new HtmlUnitDriver(true);
        driver = createDriver();
		listIssues();
		driver.quit();
    }
	
	public static WebDriver createDriver() throws Exception {
		try {
			if (driver != null) driver.quit();
		} catch (Exception e) {
			e.printStackTrace();
		}
        WebDriver driver = new FirefoxDriver();
		driver.get("https://www.capetown.gov.za/en/ServiceRequests/Pages/ServicesRequestStatus.aspx");
		driver.switchTo().frame("MSOPageViewerWebPart_WebPartWPQ2");
		return driver;
	}
}
