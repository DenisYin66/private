package com.yin.service.impl;

import java.io.Serializable;
import java.util.UUID;

/**
 * 合约交易策略配置
 * @author Administrator
 *
 */
public class HedgingConfig implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7009794771611501323L;

	private String title = "新策略";

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	private String configId = UUID.randomUUID().toString().replaceAll("-", "");

	public String getConfigId() {
		return configId;
	}

	public void setConfigId(String configId) {
		this.configId = configId;
	}
	/**
	 *  BTC,ETC...
	 */
	private String coin;
	/**
	 * type in (tt,tn,tq,nq)
	 */
	private String type;

	/**
	 * 溢价入场条件1:当季公式减去当周公式的绝对值差异数，当差异指数超过此阀值就进行套利交易
	 */
	private float dangjizhouDiffRate = 0.3f;
	/**
	 * 溢价入场条件2:当季公式除以当周公式，当奥特曼指数超过此阀值就进行套利交易
	 */
	private float atmInRate = 1.45f;

	/**
	 * 用于atmInRate的符号，0表示大于atmInRate进场，1表示小于atmInRate进场
	 */
	private int atmInSign = 0;

	/**
	 * 奥特曼指数拐点差值
	 */
	private float atmDiff = 0.01f;

	/**
	 * 溢价出场条件1:当季公式除以当周公式，当奥特曼指数低于此阀值就进行平仓套利交易
	 */
	private float atmOutRate = 1.1f;


	/**
	 * 开仓滑点%，加大买价和降低卖价，促使交易完全成交
	 */
	private float startPremiumRate = 0f;
	/**
	 * 平仓滑点%，加大买价和降低卖价，促使交易完全成交
	 */
	private float finishPremiumRate = 0f;
	/**
	 * 止盈率 %
	 */
	private float profitRate = 3f;

	/**
	 * 杠杆率
	 */
	private int leverRate = 20;

	/**
	 * 账号剩余合约交易张数
	 */
	private int volume = 1;

	/**
	 * 每笔最大交易合约张数，0不限制
	 */
	private int maxTradeVolume = 1;

	/**
	 * 距离交割时间在多少小时内不开仓，目前以星期五交割日16:00交割,值为0代表不限制
	 */
	private int lastHegingHour = 5;

	/**
	 * 执行的策略类型 目前有 【0：当季做空，当周做多 1: 当季做多，当周做空】
	 */
	private int typeAction = 0;

	/**
	 * 对冲交易成交后，多少小时未止盈平仓的，采取强制平仓策略，0为不强制平仓。和可接受的强制平仓止损率LiquidRate一起配合使用
	 */
	private int maxHedgingHour = 0;

	private int buyLevel = 1;// 使用买几价来匹配，买一价，买二价，买三价，依此类推。
	private int sellLevel = 1;// 使用卖几价来匹配，卖一价，卖二价，卖三价，依此类推。
	/**
	 * 是否开始对冲套利
	 */
	private boolean start;
	/**
	 * 开仓最低剩余委托金额（美元），对冲完成后剩余的买卖双方买卖价格线的的总委托金额必须大于等于这个阀值才开启对冲，为了防止对冲时对冲失败
	 */
	private float startThresholdAmount=500;
	/**
	 * 平仓最低剩余委托金额（美元），对冲完成后剩余的买卖双方买卖价格线的的总委托金额必须大于等于这个阀值才开启对冲，为了防止对冲时对冲失败
	 */
	private float finishThresholdAmount=500;

	public float getFinishThresholdAmount() {
		return finishThresholdAmount;
	}

	public void setFinishThresholdAmount(float finishThresholdAmount) {
		this.finishThresholdAmount = finishThresholdAmount;
	}

	public float getStartThresholdAmount() {
		return startThresholdAmount;
	}

	public void setStartThresholdAmount(float startThresholdAmount) {
		this.startThresholdAmount = startThresholdAmount;
	}

	public int getBuyLevel() {
		return buyLevel;
	}

	public void setBuyLevel(int buyLevel) {
		this.buyLevel = buyLevel;
	}

	public int getSellLevel() {
		return sellLevel;
	}

	public void setSellLevel(int sellLevel) {
		this.sellLevel = sellLevel;
	}

	public int getAtmInSign() {
		return atmInSign;
	}

	public void setAtmInSign(int atmInSign) {
		this.atmInSign = atmInSign;
	}

	public boolean isStart() {
		return start;
	}

	public void setStart(boolean start) {
		this.start = start;
	}

	public float getProfitRate() {
		return profitRate;
	}

	public float getDangjizhouDiffRate() {
		return dangjizhouDiffRate;
	}

	public void setDangjizhouDiffRate(float dangjizhouDiffRate) {
		this.dangjizhouDiffRate = dangjizhouDiffRate;
	}

	public float getAtmInRate() {
		return atmInRate;
	}

	public void setAtmInRate(float atmInRate) {
		this.atmInRate = atmInRate;
	}

	public float getAtmOutRate() {
		return atmOutRate;
	}

	public void setAtmOutRate(float atmOutRate) {
		this.atmOutRate = atmOutRate;
	}

	public void setProfitRate(float profitRate) {
		this.profitRate = profitRate;
	}

	public int getLeverRate() {
		return leverRate;
	}

	public void setLeverRate(int leverRate) {
		this.leverRate = leverRate;
	}

	public int getVolume() {
		return volume;
	}

	public synchronized void setVolume(int volume) {
		this.volume = volume;
	}

	public int getMaxTradeVolume() {
		return maxTradeVolume;
	}

	public void setMaxTradeVolume(int maxTradeVolume) {
		this.maxTradeVolume = maxTradeVolume;
	}

	public int getMaxHedgingHour() {
		return maxHedgingHour;
	}

	public void setMaxHedgingHour(int maxHedgingHour) {
		this.maxHedgingHour = maxHedgingHour;
	}


	public int getLastHegingHour() {
		return lastHegingHour;
	}

	public void setLastHegingHour(int lastHegingHour) {
		this.lastHegingHour = lastHegingHour;
	}

	public String getCoin() {
		return coin;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	public void setCoin(String coin) {
		this.coin = coin;
	}
	public int getTypeAction() {
		return typeAction;
	}

	public void setTypeAction(int typeAction) {
		this.typeAction = typeAction;
	}
	public float getStartPremiumRate() {
		return startPremiumRate;
	}

	public void setStartPremiumRate(float startPremiumRate) {
		this.startPremiumRate = startPremiumRate;
	}

	public float getFinishPremiumRate() {
		return finishPremiumRate;
	}

	public void setFinishPremiumRate(float finishPremiumRate) {
		this.finishPremiumRate = finishPremiumRate;
	}

	public float getAtmDiff() {
		return atmDiff;
	}

	public void setAtmDiff(float atmDiff) {
		this.atmDiff = atmDiff;
	}
}
