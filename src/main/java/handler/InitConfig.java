package handler;

import opensource.SampleOrg;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * init config
 */
public class InitConfig {
	private long waiteTime = 100000;
	private static final Properties sdkProperties = new Properties();
	private static final HashMap<String, SampleOrg> sampleOrgs = new HashMap<>();

	private static InitConfig initConfig;
	private HashMap configMap;

	private InitConfig(String configPath) {
		File configFile = new File(System.getProperty("user.dir") + configPath);
		System.out.printf("configFile : %s\n", configFile.getAbsolutePath());
		Yaml yaml = new Yaml();
		// HashMap configMap = null;
		try {

			configMap = (HashMap) yaml.load(new FileInputStream(configFile));

			// System.out.printf("configMap : %s\n",configMap);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		setSampleOrg();
	}

	private void setSampleOrg() {
		HashMap orgMap = (HashMap) configMap.get("organizations");
		HashMap peersMap = (HashMap) configMap.get("peers");
		HashMap orderersMap = (HashMap) configMap.get("orderers");

		for (Object object : orgMap.entrySet()) {
			Map.Entry eachOrgMap = (Map.Entry) object;
			HashMap eachOrgMapValue = (HashMap) eachOrgMap.getValue();
			SampleOrg sampleOrg = new SampleOrg(eachOrgMap.getKey().toString(), eachOrgMapValue.get("mspid").toString());

			String clientKeyFile = eachOrgMapValue.get("tlsCryptoKeyPath").toString();
			String clientCertFile = eachOrgMapValue.get("tlsCryptoCertPath").toString();

			sampleOrg.setKeystorePath(((HashMap) eachOrgMapValue.get("adminPrivateKey")).get("path").toString());
			sampleOrg.setSigncertsPath(((HashMap) eachOrgMapValue.get("signedCert")).get("path").toString());

			ArrayList orgPeersArrary = (ArrayList) eachOrgMapValue.get("peers");
			for (Object eachPeer : orgPeersArrary) {
				HashMap eachPeerMap = (HashMap) peersMap.get(eachPeer.toString());

				sampleOrg.addPeerLocation(eachPeer.toString(), eachPeerMap.get("url").toString());
				sampleOrg.addEventHubLocation(eachPeer.toString(), eachPeerMap.get("eventUrl").toString());

				Properties pro = getProperties(eachPeerMap);
				pro.put("clientKeyFile", clientKeyFile);
				pro.put("clientCertFile", clientCertFile);

				System.out.printf("clientKeyFile : %s \nclientCertFile:%s\n", clientKeyFile, clientCertFile);

				sampleOrg.addPeerProperties(eachPeer.toString(), pro);
			}

			for (Object eachOrderer : orderersMap.entrySet()) {
				Map.Entry eachOrdererEntry = (Map.Entry) eachOrderer;
				HashMap eachOrdererMap = (HashMap) eachOrdererEntry.getValue();
				String url = eachOrdererMap.get("url").toString();
				sampleOrg.addOrdererLocation(eachOrdererEntry.getKey().toString(), url);

				Properties pro = getProperties(eachOrdererMap);
				// pro.put("clientKeyFile",clientKeyFile);
				// pro.put("clientCertFile",clientCertFile);

				sampleOrg.addOrdererProperties(eachOrdererEntry.getKey().toString(), pro);
			}

			sampleOrgs.put(eachOrgMap.getKey().toString(), sampleOrg);
		}
	}

	private Properties getProperties(HashMap nodeMap) {
		Properties properties = new Properties();
		HashMap grpcMap = (HashMap) nodeMap.get("grpcOptions");
		properties.setProperty("pemFile", ((HashMap) nodeMap.get("tlsCACerts")).get("path").toString());
		// properties.setProperty("hostnameOverride", grpcMap.get("hostnameOverride").toString());
		properties.setProperty("hostnameOverride", grpcMap.get("ssl-target-name-override").toString());

		// properties.setProperty("sslProvider", grpcMap.get("sslProvider").toString());
		// properties.setProperty("negotiationType", grpcMap.get("negotiationType").toString());
		return properties;
	}

	public Properties getSMProperties() {
		Properties properties = new Properties();
		properties.setProperty("org.hyperledger.fabric.sdk.hash_algorithm", "SM3");
		properties.setProperty("org.hyperledger.fabric.sdk.crypto.default_signature_userid", "1234567812345678");
		return properties;
	}

	public void setWaiteTime(long waiteTime) {
		this.waiteTime = waiteTime;
	}

	public static InitConfig getConfig(String configPath) {
		if (initConfig == null) {
			initConfig = new InitConfig(configPath);
		}
		return initConfig;
	}

	public Collection<SampleOrg> getIntegrationSampleOrgs() {
		return Collections.unmodifiableCollection(sampleOrgs.values());
	}

	public SampleOrg getIntegrationSampleOrg(String name) {
		return sampleOrgs.get(name);
	}

	public long getWaiteTime() {
		return waiteTime;
	}

	/*
	 * public static void main(String[] args) throws FileNotFoundException { new InitConfig("/src/main/fixture/config/network-config.yaml"); }
	 */

	private static void setProperty(String key, String value) {
		String ret = System.getProperty(key);
		if (ret != null) {
			sdkProperties.put(key, ret);
		} else {
			String envKey = key.toUpperCase().replaceAll("\\.", "_");
			ret = System.getenv(envKey);
			if (null != ret) {
				sdkProperties.put(key, ret);
			} else {
				if (null == sdkProperties.getProperty(key) && value != null) {
					sdkProperties.put(key, value);
				}

			}

		}
	}

}
