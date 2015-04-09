package jenkins.plugins.tanaguru;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class Utilities {

	public static String uploadFileToSftpServer(String user, String password, String host, String remotePath, String fileName) {
		int port = 22;
		String rez = "+!";
		File f = null;
		FileInputStream fis = null;

		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(user, host, port);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");

			session.connect();

			Channel channel = session.openChannel("sftp");
			channel.connect();
			ChannelSftp channelSftp = (ChannelSftp) channel;
			channelSftp.cd(remotePath);
			f = new File(fileName);
			fis = new FileInputStream(f);
			channelSftp.put(fis, f.getName());

			channel.disconnect();
			session.disconnect();
		}

		catch (Exception e) {
			rez = e.toString();
		}finally{
			if(fis!=null){
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return rez;
	}

	public static String execNixComAndGetRez(String user, String password, String host, String command, PrintStream ps) {
		int port = 22;
		String rez = "+!";

		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(user, host, port);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");

			session.connect();

			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command); //setting command

			channel.setInputStream(null);

			((ChannelExec) channel).setErrStream(System.err);

			InputStream in = channel.getInputStream();

			channel.connect();

			byte[] tmp = new byte[1024];
			while (true) {
				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					if (i < 0)
						break;
					rez = new String(tmp, 0, i);
					ps.println(rez);
				}
				if (channel.isClosed()) {
					break;
				}
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
					rez = e.toString();
				}
			}
			channel.disconnect();
			session.disconnect();
		}

		catch (Exception e) {
			rez = e.toString();
		}
		return rez;
	}

	public static String stripLeadingAndTrailingQuotes(String str){
		if (str.startsWith("\"")){
			str = str.substring(1, str.length());
		}

		if (str.endsWith("\"")){
			str = str.substring(0, str.length() - 1);
		}

		return str;
	}

}
