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
