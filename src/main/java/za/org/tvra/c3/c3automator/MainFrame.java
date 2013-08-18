package za.org.tvra.c3.c3automator;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class MainFrame extends JFrame {
	
	private static final long serialVersionUID = 1L;
	
	private JTextField apiKeyField = new JTextField();
	private JTextArea logArea = new JTextArea();
	private JButton runButton = new JButton("Run");
	private JButton stopButton = new JButton("Stop");

	private AutomatorThread thread = null;

	public MainFrame() {
		super("C3 Automator");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setMinimumSize(new Dimension(300,300));
		
		JPanel contentPane = new JPanel(new BorderLayout(10, 10));
		contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setContentPane(contentPane);
		
		Box verticalBox = Box.createVerticalBox();
		Box apiKeyBox = Box.createHorizontalBox();
//		apiKeyBox.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		apiKeyBox.add(new JLabel("API Key"));
		apiKeyBox.add(apiKeyField);
		apiKeyBox.add(runButton);
		apiKeyBox.add(stopButton);
		
		runButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (thread == null) {
					thread = new AutomatorThread(logArea);
					thread.setApiAccessKey(apiKeyField.getText());
					thread.start();
					if (!thread.isAlive()) thread = null; 
				}					
			}
		});
		
		stopButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if (thread != null) {
					thread.askToStop();
					thread = null;
				}
			}
		});
		
		verticalBox.add(apiKeyBox);
		contentPane.add(verticalBox, BorderLayout.NORTH);
//		logArea.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		logArea.setAutoscrolls(true);
		logArea.setEditable(false);
		logArea.setLineWrap(true);
		JScrollPane scrollPane = new JScrollPane(logArea);
		contentPane.add(scrollPane, BorderLayout.CENTER);
		pack();
		setVisible(true);
	}

}
