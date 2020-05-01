package com.yin.service.impl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.alibaba.fastjson.JSONArray;
import com.google.common.base.Strings;
import com.okcoin.commons.okex.open.api.utils.HmacSHA256Base64Utils;
import com.okex.websocket.MoniterTask;
import com.okex.websocket.WebSocketBase;
import com.okex.websocket.WebSocketClientHandler;
import com.yin.service.FutureInstrumentService;
import com.yin.service.WebSocketService;
import com.yin.spring.SpringContextHolder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

/**
 * 项目 主入口，okex v3 订阅数据客户端，初始化开启
 * @author yin
 * @createDate 2018年12月24日 下午3:34:19
 */
@Service("webSoketClient")
public class WebSoketClient implements WebSocketBase, ApplicationListener<ContextRefreshedEvent> {
	@Resource(name="msgCenterService")
	private WebSocketService msgCenterService;
	/**
	 * 参数配置
	 */
	@Autowired
	private SystemConfig systemConfig;
	/**
	 * 期货合约ID服务
	 */
	@Autowired
	private FutureInstrumentService futureInstrumentService;
	@PostConstruct
	private void init() {
		start();
	}

	@PreDestroy
	public void destroy() {
		if (HedgingManager.getInstance().getHedgings().size() > 0) {
			EhCacheCacheManager cacheCacheManager = SpringContextHolder.applicationContext
					.getBean(EhCacheCacheManager.class);
			Cache cache = cacheCacheManager.getCacheManager().getCache("hedgingsCache");
			cache.put(new Element("hedgings", HedgingManager.getInstance().getHedgings()));
			cache.flush();
		}
		stop();
	}
	
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (event.getApplicationContext().getParent() == null) {
			System.out.println("init subscribe okex ws V3");
			futureInstrumentService.refresh();
			List<String> subscribes = futureInstrumentService.getSubscribes();
			if (subscribes == null) {
				subscribes = new LinkedList<String>();
			}
			String[] coins = null;
			if (!Strings.isNullOrEmpty(systemConfig.getCoins())) {
				coins = systemConfig.getCoins().split(",");
			}
			if (coins != null)
				for (String coin : coins) {
					subscribes.add("futures/account:" + coin.toUpperCase());
				}
			addChannel("subscribe", subscribes);
			EhCacheCacheManager cacheCacheManager = SpringContextHolder.applicationContext
					.getBean(EhCacheCacheManager.class);
			Cache cache = cacheCacheManager.getCacheManager().getCache("hedgingsCache");
			Element element = cache.get("hedgings");
			if (element != null) {
				List<Hedging> hedgings = (List<Hedging>) element.getObjectValue();
				if (!ObjectUtils.isEmpty(hedgings)) {
					HedgingManager.getInstance().initHedgings(hedgings);
					VolumeManager.getInstance().init(hedgings);
				}
			}
		}
	}
	
	
	
	private Timer timerTask = null;
	private MoniterTask moniter = null;
	private EventLoopGroup group = null;
	private Bootstrap bootstrap = null;
	private Channel channel = null;
	private ChannelFuture future = null;
	private boolean isAlive = false;
	private Set<String> subscribChannel = new HashSet<String>();
	
	private boolean isLogin = false;

	public boolean isLogin() {
		return isLogin;
	}

	public void setLogin(boolean isLogin) {
		System.out.println("setLogin " + isLogin);
		this.isLogin = isLogin;
	}

	public void stop() {
		timerTask.cancel();
		this.group.shutdownGracefully();
		this.group = null;
	}

	public void start() {
		if (systemConfig.getOkWebSocketURL() == null) {
			return;
		}
		if (msgCenterService == null) {
			return;
		}
		moniter = new MoniterTask(this);
		this.connect();
		timerTask = new Timer();
		timerTask.schedule(moniter, 1000, 5000);
	}

	public void setStatus(boolean flag) {
		this.isAlive = flag;
	}

	public void loginV3(String apiKey, String secretKey, String passphrase) {
		long timestamp = System.currentTimeMillis() / 1000;
		String signStr = null;
		try {
			signStr = HmacSHA256Base64Utils.sign(timestamp + "", "GET", "/users/self/verify", null, null, secretKey);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<String> args = new ArrayList<>();
		args.add(apiKey);
		args.add(passphrase);
		args.add(timestamp + "");
		args.add(signStr);
		this.sendChannel("login", args);
	}

	public void addChannel(String event, String[] parameters) {
		addChannel(event, Arrays.asList(parameters));
	}

	public void addChannel(String event, Collection<String> parameters) {
		// 必须登录后再订阅
		if (parameters == null || parameters.isEmpty()) {
			return;
		}
		int size = 0;
		Set<String> tempSet = new HashSet<String>();
		for (String parameter : parameters) {
			int length = parameter.getBytes().length;
			if (size + length > 4096) {
				sendChannel(event, tempSet);
				tempSet.clear();
				size = 0;
			}
			size += length;
			tempSet.add(parameter);
		}
		sendChannel(event, tempSet);
	}

	/**
	 * 只订阅未订阅的channel
	 * 
	 * @param event
	 * @param parameters
	 */
	public void tryAddChannel(String event, Collection<String> parameters) {
		List<String> addChannel = new ArrayList<String>();
		for (String parameter : parameters) {
			if (!subscribChannel.contains(parameter)) {
				addChannel.add(parameter);
			}
		}
		addChannel(event, addChannel);
	}

	private boolean isNotLogin(String parameter) {
		return !parameter.startsWith("futures/order") && !parameter.startsWith("futures/account")
				&& !parameter.startsWith("futures/position") && !parameter.startsWith("spot/order")
				&& !parameter.startsWith("spot/account") && !parameter.startsWith("spot/margin_account");
	}

	private void sendChannel(String event, Collection<String> parameters) {
		// 必须登录后再订阅
		if (parameters == null || parameters.isEmpty()) {
			return; 
		}
		if ("subscribe".equals(event))
			subscribChannel.addAll(parameters);
		List<String> sendParameters = new LinkedList<>();
		List<String> loginsendParameters = new LinkedList<>();

		for (String parameter : parameters) {
			if (isNotLogin(parameter)) {
				sendParameters.add(parameter);
			} else {
				loginsendParameters.add(parameter);
			}
		}
//		if (!"login".equals(event) && !this.isLogin()) {
//			for (String parameter : parameters) {
//				if (isNotLogin(parameter)) {
//					sendParameters.add(parameter);
//				}
//			}
//		} else {
//			if (isNotLogin(parameter)) {
//			sendParameters.addAll(parameters);
//			}
//		}
		if (!sendParameters.isEmpty()) {
			String json = JSONArray.toJSONString(sendParameters);
			String dataMsg = "{\"op\": \"" + event + "\", \"args\":" + json + "}";
			System.out.println(dataMsg);
			this.sendMessage(dataMsg);
		}
		if (!loginsendParameters.isEmpty()) {
			String json = JSONArray.toJSONString(loginsendParameters);
			String dataMsg = "{\"op\": \"" + event + "\", \"args\":" + json + "}";
			System.out.println(dataMsg);
			this.sendMessage(dataMsg);
		}
	}

	public void removeChannel(String parameters) {
		if (parameters == null) {
			return;
		}
		String dataMsg = "{\"op\": \"unsubscribe\", \"args\":" + parameters + "}";
		this.sendMessage(dataMsg);
		subscribChannel.remove(parameters);
	}

	private void connect() {
		try {
			setLogin(false);
			final URI uri = new URI(systemConfig.getOkWebSocketURL());
			System.out.println(systemConfig.getOkWebSocketURL());
			group = new NioEventLoopGroup(1);
			bootstrap = new Bootstrap();
			final SslContext sslCtx = SslContext.newClientContext();
			final WebSocketClientHandler handler = new WebSocketClientHandler(WebSocketClientHandshakerFactory
					.newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders(), Integer.MAX_VALUE),
					msgCenterService, moniter);
			bootstrap.group(group).option(ChannelOption.TCP_NODELAY, true).channel(NioSocketChannel.class)
					.handler(new ChannelInitializer<SocketChannel>() {
						protected void initChannel(SocketChannel ch) {
							ChannelPipeline p = ch.pipeline();
							if (sslCtx != null) {
								p.addLast(sslCtx.newHandler(ch.alloc(), uri.getHost(), uri.getPort()));
							}
							p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192), handler);
						}
					});
			System.out.println("1");
			future = bootstrap.connect(uri.getHost(), uri.getPort());
			System.out.println(future.toString());
			channel = future.sync().channel();
			handler.handshakeFuture().sync().addListener(new ChannelFutureListener() {
				public void operationComplete(final ChannelFuture future) throws Exception {
					setStatus(true);
					reAddChannel();
					loginV3(systemConfig.getApiKey(), systemConfig.getSecretKey(), systemConfig.getPassphrase());
				}
			});
		} catch (Exception e) {
			System.out.println("yinzc");
			e.printStackTrace();
			group.shutdownGracefully();
			this.setStatus(false);
		}
	}

	private void sendMessage(String message) {
		if (isAlive) {
			channel.writeAndFlush(new TextWebSocketFrame(message));
		} else {
			System.out.println("is Not Alive");
		}
	}

	public void sentPing() {
		try {
			String dataMsg = "ping";
			this.sendMessage(dataMsg);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void reAddChannel() {
		if (!subscribChannel.isEmpty()) {
			this.addChannel("subscribe", subscribChannel);
		}
	}

	public void reLoginAddChannel() {
		if (!subscribChannel.isEmpty()) {
			List<String> sendParameters = new LinkedList<>();
			for (String parameter : subscribChannel) {
				if (!isNotLogin(parameter)) {
					sendParameters.add(parameter);
				}
			}
			addChannel("subscribe", sendParameters);
		}
	}

	public void reConnect() {
		try {
			this.group.shutdownGracefully();
			this.group = null;
			this.connect();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
