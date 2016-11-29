package cn.com.turingsearch.index.config;

import org.ansj.library.UserDefineLibrary;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.ansj.util.MyStaticValue;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import cn.com.turingsearch.pubsub.redis.AddTermRedisPubSub;
import cn.com.turingsearch.pubsub.redis.RedisPoolBuilder;
import cn.com.turingsearch.pubsub.redis.RedisUtils;
import cn.com.turingsearch.util.FileDeEncrypt;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class AnsjElasticConfigurator {
	public static ESLogger logger = Loggers.getLogger("ansj-initializer");
	private static volatile boolean loaded = false;
	public static Set<String> filter;
	public static Environment environment;
	public static String DEFAULT_USER_LIB_PATH = "ansj/dic/user/";
	public static String DEFAULT_REDIS_LIB_PATH = DEFAULT_USER_LIB_PATH + "ext.dic";
	public static File REDIS_LIB_FILE = null;
	public static String DEFAULT_AMB_FILE_LIB_PATH = "ansj/dic/ambiguity.dic";
	public static String DEFAULT_STOP_FILE_LIB_PATH = "ansj/dic/stopLibrary.dic";
	public static boolean DEFAULT_IS_NAME_RECOGNITION = true;
	public static boolean DEFAULT_IS_NUM_RECOGNITION = true;
	public static boolean DEFAUT_IS_QUANTIFIE_RRECOGNITION = false;
	public static FileDeEncrypt deEncrypt = new FileDeEncrypt("TURINGSEARCH FILE DE-ENCRYPT");

	public static void init(Settings settings, Environment env) {
		if (isLoaded()) {
			return;
		}
		environment = env;
		Settings ansjSettings = settings.getAsSettings("ansj");
		initConfig(ansjSettings, env);
		boolean enabledStopFilter = ansjSettings.getAsBoolean("enabled_stop_filter", true);
		if (enabledStopFilter) {
			loadFilter(ansjSettings, env);
		}
		preheat();
		logger.info("ansj分词器预热完毕，可以使用!");
		initRedis(ansjSettings);
		setLoaded(true);
	}

	private static void initRedis(final Settings settings) {
		if (null == settings.get("redis.ip")) {
			logger.info("没有找到redis相关配置!");
			return;
		}
		loadRedisLib(settings);
		new Thread(new Runnable() {
			@Override
			public void run() {
				RedisPoolBuilder redisPoolBuilder = new RedisPoolBuilder();
				int maxActive = settings.getAsInt("redis.pool.maxactive", redisPoolBuilder.getMaxActive());
				int maxIdle = settings.getAsInt("redis.pool.maxidle", redisPoolBuilder.getMaxIdle());
				int maxWait = settings.getAsInt("redis.pool.maxwait", redisPoolBuilder.getMaxWait());
				boolean testOnBorrow = settings.getAsBoolean("redis.pool.testonborrow",
						redisPoolBuilder.isTestOnBorrow());
				logger.debug("maxActive:{},maxIdle:{},maxWait:{},testOnBorrow:{}", maxActive, maxIdle, maxWait,
						testOnBorrow);
				String ipAndport = settings.get("redis.ip", redisPoolBuilder.getIpAddress());
				int port = settings.getAsInt("redis.port", redisPoolBuilder.getPort());
				int timeout = settings.getAsInt("redis.timeout", redisPoolBuilder.getTimeout());
				String password = settings.get("redis.password");
				String channel = settings.get("redis.channel", "ansj_term");
				logger.debug("ip:{},port:{},timeout:{},auth:{},channel:{}", ipAndport, port, timeout, password != null,
						channel);
				JedisPool pool = redisPoolBuilder.setMaxActive(maxActive).setMaxIdle(maxIdle).setMaxWait(maxWait)
						.setTestOnBorrow(testOnBorrow).setIpAddress(ipAndport).setPort(port).setTimeout(timeout)
						.setPassword(password).jedisPool();
				RedisUtils.setJedisPool(pool);
				final Jedis jedis = RedisUtils.getConnection();
				logger.debug("pool:{},jedis:{}", pool == null, jedis == null);
				logger.info("redis守护线程准备完毕,ip:{},port:{},timeout:{},auth:{},channel:{}", ipAndport, port, timeout,
						password != null, channel);
				Objects.requireNonNull(jedis);
				jedis.subscribe(new AddTermRedisPubSub(), channel);
				RedisUtils.closeConnection(jedis);

			}
		}).start();

	}

	private static void preheat() {
		ToAnalysis.parse("这是一个基于ansj的分词插件");
	}

	private static void initConfig(Settings settings, Environment environment) {

		Path path = environment.configFile().resolve(settings.get("dic_path", DEFAULT_USER_LIB_PATH));
		MyStaticValue.DIC.put(MyStaticValue.DIC_DEFAULT, path.toAbsolutePath().toString());
		logger.debug("用户词典路径:{}", path.toAbsolutePath().toString());

		path = environment.configFile().resolve(settings.get("ambiguity_path", DEFAULT_AMB_FILE_LIB_PATH));
		MyStaticValue.ambiguityLibrary = path.toAbsolutePath().toString();
		logger.debug("歧义词典路径:{}", MyStaticValue.ambiguityLibrary);
		// todo 目前没有使用
		// path =
		// environment.configFile().resolve(settings.get("crf_model_path","ansj/dic/crf.model"));
		// MyStaticValue.crfModel = path.toAbsolutePath().toString();
		// logger.debug("crfModel:{}",MyStaticValue.crfModel );

        // 是否显示真实词语
		MyStaticValue.isRealName = true;

        // 是否开启人名识别
		MyStaticValue.isNameRecognition = settings.getAsBoolean("enable_name_recognition", DEFAULT_IS_NAME_RECOGNITION);

        // 是否开启数字识别
		MyStaticValue.isNumRecognition = settings.getAsBoolean("enable_num_recognition", DEFAULT_IS_NUM_RECOGNITION);

        // 是否数字和量词合并
		MyStaticValue.isQuantifierRecognition = settings.getAsBoolean("enable_quantifier_recognition",
				DEFAUT_IS_QUANTIFIE_RRECOGNITION);

        // 是否用户词典不加载相同的词
		MyStaticValue.isSkipUserDefine = settings.getAsBoolean("enable_skip_user_define", MyStaticValue.isSkipUserDefine);

		// init default用户自定义词典
		File defaultPath = null;
		try {
			String jarPath = java.net.URLDecoder.decode(
					AnsjElasticConfigurator.class.getProtectionDomain().getCodeSource().getLocation().getFile(),
					"UTF-8");
			defaultPath = new File(new File(jarPath).getParent(), "default.dic");
			UserDefineLibrary.loadFile(UserDefineLibrary.FOREST, defaultPath);
			logger.debug("加载系统内置词典:{} 成功!", defaultPath.getAbsolutePath());
		} catch (UnsupportedEncodingException e) {
			logger.error("加载系统内置词典:{} 失败!", defaultPath);
		}

	}

	private static void loadFilter(Settings settings, Environment environment) {
		Set<String> filters = new HashSet<>();
		String stopLibraryPath = settings.get("stop_path", DEFAULT_STOP_FILE_LIB_PATH);
		if (stopLibraryPath == null) {
			return;
		}

		File stopLibrary = new File(environment.configFile().toFile(), stopLibraryPath);
		logger.debug("停止词典路径:{}", stopLibrary.getAbsolutePath());
		if (!stopLibrary.isFile()) {
			logger.info("Can't find the file:{}, no such file or directory exists!", stopLibraryPath);
			emptyFilter();
			return;
		}

		try(BufferedReader br = deEncrypt.decryptFile(stopLibrary.getAbsolutePath())){
			String temp;
			while ((temp = br.readLine()) != null) {
				filters.add(temp);
			}
		} catch (IOException e) {
			logger.info("ansj停用词典加载出错!");
		}
		filter = filters;
		logger.info("ansj停止词典加载完毕!");
	}

	private static void loadRedisLib(Settings settings) {
		REDIS_LIB_FILE = environment.configFile().resolve(settings.get("redis.write.dic", DEFAULT_REDIS_LIB_PATH))
				.toFile();
		logger.debug("redis词典路径:{}", REDIS_LIB_FILE.getAbsolutePath());
		if (!REDIS_LIB_FILE.isFile()) {
			logger.info("Can't find the file:{}, no such file exists!", REDIS_LIB_FILE.getAbsolutePath());
			return;
		}

		try (BufferedReader br = deEncrypt.decryptFile(REDIS_LIB_FILE.getAbsolutePath())) {
			String temp;
			while ((temp = br.readLine()) != null) {
				UserDefineLibrary.insertWord(temp, "userDefine", 1000);
			}
		} catch (IOException e) {
			logger.error("加载redis词典:{} 失败!", REDIS_LIB_FILE.getAbsolutePath());
		}
		logger.info("加载redis词典:{} 成功!", REDIS_LIB_FILE.getAbsolutePath());
	}

	private static void emptyFilter() {
		filter = new HashSet<>();
	}

	public static boolean isLoaded() {
		return loaded;
	}

	public static void setLoaded(boolean loaded) {
		AnsjElasticConfigurator.loaded = loaded;
	}

}
