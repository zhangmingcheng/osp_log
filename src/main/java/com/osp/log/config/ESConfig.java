package com.osp.log.config;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * 
 * @author fly
 *
 */
@Configuration
@PropertySource("classpath:elastic.properties")
public class ESConfig {

	public static String ES_CLUSTERNAME;
	public static String ES_IP;
	public static int ES_PORT;
	public static String SEARCHINDEX;
	public static String SEARCHTYPE;

	@Value("${es.clusterName}")
	private String clusterName;
	@Value("${es.ip}")
	private String ip;
	@Value("${es.port}")
	private int port;
	@Value("${es.searchindex}")
	private String searchindex;
	@Value("${es.searchtype}")
	private String searchtype;

	@PostConstruct
	public void init() {
		ESConfig.ES_CLUSTERNAME = this.clusterName;
		ESConfig.ES_PORT = this.port;
		ESConfig.ES_IP = this.ip;
		ESConfig.SEARCHINDEX = this.searchindex;
		ESConfig.SEARCHTYPE = this.searchtype;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
}
