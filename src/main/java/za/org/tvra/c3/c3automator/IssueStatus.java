/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package za.org.tvra.c3.c3automator;

public enum IssueStatus {
 NEW(1), 
 INPROGRESS(2), 
 C3UNCONFIRMED(7), 
 C3UNSATISFACTORY(9),
 C3CONFIRMED(3),
 UNKNOWN(0);
 
 private int code;
 
 private IssueStatus(int c) {
   code = c;
 }
 
 public int getCode() {
   return code;
 }
 
 public static IssueStatus findByCode(int c) {
	 for (IssueStatus status : IssueStatus.values()) {
		 if (status.getCode() == c) return status;
	 }
	 return UNKNOWN;
 }
 
}
