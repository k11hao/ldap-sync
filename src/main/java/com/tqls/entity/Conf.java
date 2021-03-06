package com.tqls.entity;

import java.util.List;

/**
 * 配置信息
 */
public class Conf {

    /**
     * 数据源
     */
    private Server source;
    /**
     * 同步到目标服务器信息
     */
    private List<Server> target;
    /**
     * 分钟
     */
    private long period;

    private int serverPort;

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * 是否立即启动
     * @return
     */
    private boolean startEnable;

    public boolean isStartEnable() {
        return startEnable;
    }

    public void setStartEnable(boolean startEnable) {
        this.startEnable = startEnable;
    }

    public Server getSource() {
        return source;
    }

    public void setSource(Server source) {
        this.source = source;
    }

    public List<Server> getTarget() {
        return target;
    }

    public void setTarget(List<Server> target) {
        this.target = target;
    }

    public long getPeriod() {
        return period;
    }

    public void setPeriod(long period) {
        this.period = period;
    }
}
