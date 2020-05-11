package application.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;



public class Test4 {
	
	private static String getImageDigest(String url, String user, String password, String repository, String imageName, String tag) throws IOException {
		String digest=null;
		String parm1 = "inspect";
		String parm2 = "--creds";
		String parm3 = user+":"+password;
		String parm4 = url+"/"+repository+"/"+imageName+":"+tag;
		ProcessBuilder processBuilder = new ProcessBuilder();
		StringBuilder sb = null;
		try { 
			String[] command = {"/usr/local/bin/skopeo", parm1, parm2, parm3, parm4};
			for (String s:command) {
				System.out.print(" "+s);
			}
			System.out.println("");
			processBuilder.command(command);



			Process process = processBuilder.start();
			process.waitFor();
			// blocked :(
			BufferedReader reader =
					new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line;
			sb = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
				sb.append(line);
			}

		}  catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}   




		digest=sb.toString();
		return digest;
	}
	
	

	public static void main(String[] args) {
		String url = "docker://docker.io";
		String digest=null;
		try {
			digest = getImageDigest(url,"davco01a", "Tu76512r!", "kabanero", "java-openliberty", "0.2.3");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("digest="+digest);
	}

}
