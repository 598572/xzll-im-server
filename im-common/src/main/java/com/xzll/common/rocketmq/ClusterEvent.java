package com.xzll.common.rocketmq;

import java.util.Date;

/**
 * @Author: hzz
 * @Date: 2023/3/1 09:31:02
 * @Description: 消息 对象载体
 */
public class ClusterEvent {

	//某topic下的事件类型
	protected Integer clusterEventType;

	//用于生产端和消费端的负载均衡
	protected String balanceId;

	//业务数据
	protected String data;

	//消息创建时间
	protected Date createTime = new Date();

	//消息过期时间
//	protected int ttl;


	public Integer getClusterEventType() {
		return clusterEventType;
	}

	public void setClusterEventType(Integer clusterEventType) {
		this.clusterEventType = clusterEventType;
	}

	public String getBalanceId() {
		return balanceId;
	}

	public void setBalanceId(String balanceId) {
		this.balanceId = balanceId;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

//	public int getTtl() {
//		return ttl;
//	}
//
//	public void setTtl(int ttl) {
//		this.ttl = ttl;
//	}


	@Override
	public String toString() {
		return "ClusterEvent{" +
				"balanceId='" + balanceId + '\'' +
				", data='" + data + '\'' +
				", createTime=" + createTime +
				'}';
	}
}
