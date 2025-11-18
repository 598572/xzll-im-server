package com.xzll.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redisson工具类
 * 提供Redis五种数据类型及高级功能的操作
 */
@Slf4j
@Component
public class RedissonUtils {

    @Autowired
    private RedissonClient redissonClient;

    // ==================== String类型操作 ====================

    /**
     * 设置字符串值
     */
    public void setString(String key, String value) {
        try {
            redissonClient.getBucket(key).set(value);
        } catch (Exception e) {
            log.error("设置字符串失败: key={}, value={}", key, value, e);
            throw e;
        }
    }

    /**
     * 设置字符串值并设置过期时间
     */
    public void setString(String key, String value, long timeout, TimeUnit unit) {
        try {
            redissonClient.getBucket(key).set(value, timeout, unit);
        } catch (Exception e) {
            log.error("设置字符串失败: key={}, value={}, timeout={}", key, value, timeout, e);
            throw e;
        }
    }

    /**
     * 获取字符串值
     */
    public String getString(String key) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.get();
        } catch (Exception e) {
            log.error("获取字符串失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 删除字符串
     */
    public boolean deleteString(String key) {
        try {
            return redissonClient.getBucket(key).delete();
        } catch (Exception e) {
            log.error("删除字符串失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 检查字符串是否存在
     */
    public boolean existsString(String key) {
        try {
            return redissonClient.getBucket(key).isExists();
        } catch (Exception e) {
            log.error("检查字符串存在失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return redissonClient.getBucket(key).expire(timeout, unit);
        } catch (Exception e) {
            log.error("设置过期时间失败: key={}, timeout={}", key, timeout, e);
            throw e;
        }
    }

    /**
     * 获取剩余过期时间
     */
    public long getExpire(String key, TimeUnit unit) {
        try {
            return redissonClient.getBucket(key).remainTimeToLive();
        } catch (Exception e) {
            log.error("获取过期时间失败: key={}", key, e);
            throw e;
        }
    }

    // ==================== Hash类型操作 ====================

    /**
     * 设置Hash字段
     */
    public void setHash(String key, String field, String value) {
        try {
            redissonClient.getMap(key).put(field, value);
        } catch (Exception e) {
            log.error("设置Hash字段失败: key={}, field={}, value={}", key, field, value, e);
            throw e;
        }
    }

    /**
     * 批量设置Hash字段
     */
    public void setHash(String key, Map<String, String> map) {
        try {
            redissonClient.getMap(key).putAll(map);
        } catch (Exception e) {
            log.error("批量设置Hash字段失败: key={}, map={}", key, map, e);
            throw e;
        }
    }

    /**
     * 获取Hash字段值
     */
    public String getHash(String key, String field) {
        try {
            return (String) redissonClient.getMap(key).get(field);
        } catch (Exception e) {
            log.error("获取Hash字段失败: key={}, field={}", key, field, e);
            throw e;
        }
    }

    /**
     * 批量获取Hash字段值（HMGET）
     * 用于一次性获取多个字段，性能优于多次HGET
     * 
     * @param key Hash的key
     * @param fields 字段列表
     * @return 字段-值映射（不存在的字段返回null）
     */
    public Map<String, String> batchGetHash(String key, List<String> fields) {
        try {
            if (fields == null || fields.isEmpty()) {
                return new HashMap<>();
            }
            
            RMap<Object, Object> map = redissonClient.getMap(key);
            Map<Object, Object> values = map.getAll(new HashSet<>(fields));
            
            Map<String, String> result = new HashMap<>(fields.size());
            for (String field : fields) {
                Object value = values.get(field);
                if (value != null) {
                    result.put(field, value.toString());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("批量获取Hash字段失败: key={}, fields size={}", key, fields.size(), e);
            throw e;
        }
    }

    /**
     * 获取Hash所有字段
     */
    public Map<String, String> getAllHash(String key) {
        try {
            Map<Object, Object> map = redissonClient.getMap(key).readAllMap();
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue().toString());
            }
            return result;
        } catch (Exception e) {
            log.error("获取Hash所有字段失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 删除Hash字段
     */
    public long deleteHash(String key, String... fields) {
        try {
            return redissonClient.getMap(key).fastRemove(fields);
        } catch (Exception e) {
            log.error("删除Hash字段失败: key={}, fields={}", key, Arrays.toString(fields), e);
            throw e;
        }
    }

    /**
     * 检查Hash字段是否存在
     */
    public boolean existsHash(String key, String field) {
        try {
            return redissonClient.getMap(key).containsKey(field);
        } catch (Exception e) {
            log.error("检查Hash字段存在失败: key={}, field={}", key, field, e);
            throw e;
        }
    }

    /**
     * 获取Hash字段数量
     */
    public long sizeHash(String key) {
        try {
            return redissonClient.getMap(key).size();
        } catch (Exception e) {
            log.error("获取Hash字段数量失败: key={}", key, e);
            throw e;
        }
    }

    // ==================== List类型操作 ====================

    /**
     * 从左侧推入List
     */
    public boolean pushLeft(String key, String... values) {
        try {
            RList<String> list = redissonClient.getList(key);
            return list.addAll(0, Arrays.asList(values));
        } catch (Exception e) {
            log.error("左侧推入List失败: key={}, values={}", key, Arrays.toString(values), e);
            throw e;
        }
    }

    /**
     * 从右侧推入List
     */
    public boolean pushRight(String key, String... values) {
        try {
            RList<String> list = redissonClient.getList(key);
            return list.addAll(Arrays.asList(values));
        } catch (Exception e) {
            log.error("右侧推入List失败: key={}, values={}", key, Arrays.toString(values), e);
            throw e;
        }
    }

    /**
     * 从左侧弹出List
     */
    public String popLeft(String key) {
        try {
            Object obj = redissonClient.getList(key).remove(0);
            return obj != null ? obj.toString() : null;
        } catch (Exception e) {
            log.error("左侧弹出List失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 从右侧弹出List
     */
    public String popRight(String key) {
        try {
            RList<String> list = redissonClient.getList(key);
            return list.remove(list.size() - 1);
        } catch (Exception e) {
            log.error("右侧弹出List失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 获取List指定范围
     */
    public List<String> getListRange(String key, int start, int end) {
        try {
            List<Object> objects = redissonClient.getList(key).range(start, end);
            List<String> result = new ArrayList<>();
            for (Object obj : objects) {
                result.add(obj.toString());
            }
            return result;
        } catch (Exception e) {
            log.error("获取List范围失败: key={}, start={}, end={}", key, start, end, e);
            throw e;
        }
    }

    /**
     * 获取List长度
     */
    public long sizeList(String key) {
        try {
            return redissonClient.getList(key).size();
        } catch (Exception e) {
            log.error("获取List长度失败: key={}", key, e);
            throw e;
        }
    }

    // ==================== Set类型操作 ====================

    /**
     * 添加Set元素
     */
    public boolean addSet(String key, String... values) {
        try {
            return redissonClient.getSet(key).addAll(Arrays.asList(values));
        } catch (Exception e) {
            log.error("添加Set元素失败: key={}, values={}", key, Arrays.toString(values), e);
            throw e;
        }
    }

    /**
     * 删除Set元素
     */
    public boolean removeSet(String key, String... values) {
        try {
            return redissonClient.getSet(key).removeAll(Arrays.asList(values));
        } catch (Exception e) {
            log.error("删除Set元素失败: key={}, values={}", key, Arrays.toString(values), e);
            throw e;
        }
    }

    /**
     * 获取Set所有元素
     */
    public Set<String> getAllSet(String key) {
        try {
            Set<Object> objects = redissonClient.getSet(key).readAll();
            Set<String> result = new HashSet<>();
            for (Object obj : objects) {
                result.add(obj.toString());
            }
            return result;
        } catch (Exception e) {
            log.error("获取Set所有元素失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 检查Set元素是否存在
     */
    public boolean existsSet(String key, String value) {
        try {
            return redissonClient.getSet(key).contains(value);
        } catch (Exception e) {
            log.error("检查Set元素存在失败: key={}, value={}", key, value, e);
            throw e;
        }
    }

    /**
     * 获取Set大小
     */
    public long sizeSet(String key) {
        try {
            return redissonClient.getSet(key).size();
        } catch (Exception e) {
            log.error("获取Set大小失败: key={}", key, e);
            throw e;
        }
    }

    // ==================== ZSet类型操作 ====================

    /**
     * 添加ZSet元素
     */
    public boolean addZSet(String key, String value, double score) {
        try {
            return redissonClient.getScoredSortedSet(key).add(score, value);
        } catch (Exception e) {
            log.error("添加ZSet元素失败: key={}, value={}, score={}", key, value, score, e);
            throw e;
        }
    }

    /**
     * 批量添加ZSet元素
     */
    public long addZSet(String key, Map<String, Double> scoreMembers) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            return zset.addAll(scoreMembers);
        } catch (Exception e) {
            log.error("批量添加ZSet元素失败: key={}, scoreMembers={}", key, scoreMembers, e);
            throw e;
        }
    }

    /**
     * 获取ZSet元素分数
     */
    public Double getZSetScore(String key, String value) {
        try {
            return redissonClient.getScoredSortedSet(key).getScore(value);
        } catch (Exception e) {
            log.error("获取ZSet元素分数失败: key={}, value={}", key, value, e);
            throw e;
        }
    }

    /**
     * 获取ZSet指定范围（按分数升序）
     */
    public Collection<String> getZSetRange(String key, int start, int end) {
        try {
            Collection<Object> objects = redissonClient.getScoredSortedSet(key).valueRange(start, end);
            Collection<String> result = new ArrayList<>();
            for (Object obj : objects) {
                result.add(obj.toString());
            }
            return result;
        } catch (Exception e) {
            log.error("获取ZSet范围失败: key={}, start={}, end={}", key, start, end, e);
            throw e;
        }
    }

    /**
     * 获取ZSet指定范围（按分数降序）
     */
    public Collection<String> getZSetRevRange(String key, int start, int end) {
        try {
            Collection<Object> objects = redissonClient.getScoredSortedSet(key).valueRangeReversed(start, end);
            Collection<String> result = new ArrayList<>();
            for (Object obj : objects) {
                result.add(obj.toString());
            }
            return result;
        } catch (Exception e) {
            log.error("获取ZSet倒序范围失败: key={}, start={}, end={}", key, start, end, e);
            throw e;
        }
    }

    /**
     * 获取ZSet大小
     */
    public long sizeZSet(String key) {
        try {
            return redissonClient.getScoredSortedSet(key).size();
        } catch (Exception e) {
            log.error("获取ZSet大小失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 添加元素到ZSet
     */
    public boolean addToZSet(String key, String value, double score) {
        try {
            return redissonClient.getScoredSortedSet(key).add(score, value);
        } catch (Exception e) {
            log.error("添加ZSet元素失败: key={}, value={}, score={}", key, value, score, e);
            throw e;
        }
    }

    /**
     * 添加元素到ZSet（使用Long类型的score）
     */
    public boolean addToZSet(String key, String value, long score) {
        try {
            return redissonClient.getScoredSortedSet(key).add((double) score, value);
        } catch (Exception e) {
            log.error("添加ZSet元素失败: key={}, value={}, score={}", key, value, score, e);
            throw e;
        }
    }

    /**
     * 根据分数范围删除ZSet元素
     */
    public long removeZSetByScore(String key, double startScore, double endScore) {
        try {
            return redissonClient.getScoredSortedSet(key).removeRangeByScore(startScore, true, endScore, true);
        } catch (Exception e) {
            log.error("根据分数删除ZSet元素失败: key={}, startScore={}, endScore={}", key, startScore, endScore, e);
            throw e;
        }
    }

    /**
     * 根据分数范围删除ZSet元素（使用Long类型的score）
     */
    public long removeZSetByScore(String key, long startScore, long endScore) {
        try {
            return redissonClient.getScoredSortedSet(key).removeRangeByScore((double) startScore, true, (double) endScore, true);
        } catch (Exception e) {
            log.error("根据分数删除ZSet元素失败: key={}, startScore={}, endScore={}", key, startScore, endScore, e);
            throw e;
        }
    }

    /**
     * 根据分数范围获取ZSet元素（用于扫描到期消息）
     */
    public Collection<String> getZSetRangeByScore(String key, double minScore, double maxScore) {
        try {
            Collection<Object> objects = redissonClient.getScoredSortedSet(key)
                .valueRange(minScore, true, maxScore, true);
            Collection<String> result = new ArrayList<>();
            for (Object obj : objects) {
                result.add(obj.toString());
            }
            return result;
        } catch (Exception e) {
            log.error("根据分数范围获取ZSet元素失败: key={}, minScore={}, maxScore={}", key, minScore, maxScore, e);
            throw e;
        }
    }

    /**
     * 根据分数范围获取ZSet元素（使用Long类型的score）
     */
    public Collection<String> getZSetRangeByScore(String key, long minScore, long maxScore) {
        return getZSetRangeByScore(key, (double) minScore, (double) maxScore);
    }

    /**
     * 根据分数范围获取ZSet元素（带LIMIT限制，用于批量处理）
     * @param key ZSet的key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @param limit 限制返回数量（避免一次性返回太多数据）
     * @return 元素集合
     */
    public Collection<String> getZSetRangeByScore(String key, double minScore, double maxScore, int limit) {
        try {
            Collection<Object> objects = redissonClient.getScoredSortedSet(key)
                .valueRange(minScore, true, maxScore, true, 0, limit - 1);
            Collection<String> result = new ArrayList<>();
            for (Object obj : objects) {
                result.add(obj.toString());
            }
            return result;
        } catch (Exception e) {
            log.error("根据分数范围获取ZSet元素失败: key={}, minScore={}, maxScore={}, limit={}", 
                key, minScore, maxScore, limit, e);
            throw e;
        }
    }

    /**
     * 根据分数范围获取ZSet元素（使用Long类型的score，带LIMIT限制）
     */
    public Collection<String> getZSetRangeByScore(String key, long minScore, long maxScore, int limit) {
        return getZSetRangeByScore(key, (double) minScore, (double) maxScore, limit);
    }

    /**
     * 根据分数范围获取ZSet元素（使用StringCodec，用于读取JsonJacksonCodec写入的数据）
     * @param key ZSet的key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @param limit 限制返回数量
     * @return 元素集合
     */
    public Collection<String> getZSetRangeByScoreWithStringCodec(String key, double minScore, double maxScore, int limit) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key, org.redisson.client.codec.StringCodec.INSTANCE);
            return zset.valueRange(minScore, true, maxScore, true, 0, limit - 1);
        } catch (Exception e) {
            log.error("根据分数范围获取ZSet元素失败(StringCodec): key={}, minScore={}, maxScore={}, limit={}", 
                key, minScore, maxScore, limit, e);
            throw e;
        }
    }

    /**
     * 根据分数范围获取ZSet元素（使用StringCodec + Long类型的score）
     */
    public Collection<String> getZSetRangeByScoreWithStringCodec(String key, long minScore, long maxScore, int limit) {
        return getZSetRangeByScoreWithStringCodec(key, (double) minScore, (double) maxScore, limit);
    }

    /**
     * 删除ZSet中的指定元素（用于删除特定消息）
     */
    public boolean removeZSetValue(String key, String value) {
        try {
            return redissonClient.getScoredSortedSet(key).remove(value);
        } catch (Exception e) {
            log.error("删除ZSet元素失败: key={}, value={}", key, value, e);
            throw e;
        }
    }

    /**
     * 删除ZSet中的指定元素（使用StringCodec，用于删除JsonJacksonCodec写入的数据）
     */
    public boolean removeZSetValueWithStringCodec(String key, String value) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key, org.redisson.client.codec.StringCodec.INSTANCE);
            return zset.remove(value);
        } catch (Exception e) {
            log.error("删除ZSet元素失败(StringCodec): key={}, value={}", key, value, e);
            throw e;
        }
    }

    // ==================== 高级功能 ====================

    /**
     * 分布式锁
     */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    /**
     * 公平锁
     */
    public RLock getFairLock(String lockKey) {
        return redissonClient.getFairLock(lockKey);
    }

    /**
     * 读写锁
     */
    public RReadWriteLock getReadWriteLock(String lockKey) {
        return redissonClient.getReadWriteLock(lockKey);
    }

    /**
     * 信号量
     */
    public RSemaphore getSemaphore(String semaphoreKey) {
        return redissonClient.getSemaphore(semaphoreKey);
    }

    /**
     * 计数器
     */
    public RAtomicLong getAtomicLong(String counterKey) {
        return redissonClient.getAtomicLong(counterKey);
    }

    /**
     * 限流器
     */
    public RRateLimiter getRateLimiter(String rateLimiterKey) {
        return redissonClient.getRateLimiter(rateLimiterKey);
    }

    /**
     * 布隆过滤器
     */
    public RBloomFilter<String> getBloomFilter(String bloomFilterKey) {
        return redissonClient.getBloomFilter(bloomFilterKey);
    }

    /**
     * 初始化布隆过滤器
     */
    public void initBloomFilter(String bloomFilterKey, long expectedInsertions, double falseProbability) {
        try {
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(bloomFilterKey);
            bloomFilter.tryInit(expectedInsertions, falseProbability);
        } catch (Exception e) {
            log.error("初始化布隆过滤器失败: key={}, expectedInsertions={}, falseProbability={}", 
                    bloomFilterKey, expectedInsertions, falseProbability, e);
            throw e;
        }
    }

    /**
     * 添加元素到布隆过滤器
     */
    public boolean addToBloomFilter(String bloomFilterKey, String value) {
        try {
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(bloomFilterKey);
            return bloomFilter.add(value);
        } catch (Exception e) {
            log.error("添加元素到布隆过滤器失败: key={}, value={}", bloomFilterKey, value, e);
            throw e;
        }
    }

    /**
     * 检查元素是否在布隆过滤器中
     */
    public boolean containsInBloomFilter(String bloomFilterKey, String value) {
        try {
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(bloomFilterKey);
            return bloomFilter.contains(value);
        } catch (Exception e) {
            log.error("检查布隆过滤器元素失败: key={}, value={}", bloomFilterKey, value, e);
            throw e;
        }
    }

    /**
     * BitMap操作
     */
    public RBitSet getBitSet(String bitSetKey) {
        return redissonClient.getBitSet(bitSetKey);
    }

    /**
     * 设置BitMap位
     */
    public void setBit(String bitSetKey, long bitIndex, boolean value) {
        try {
            redissonClient.getBitSet(bitSetKey).set(bitIndex, value);
        } catch (Exception e) {
            log.error("设置BitMap位失败: key={}, bitIndex={}, value={}", bitSetKey, bitIndex, value, e);
            throw e;
        }
    }

    /**
     * 获取BitMap位
     */
    public boolean getBit(String bitSetKey, long bitIndex) {
        try {
            return redissonClient.getBitSet(bitSetKey).get(bitIndex);
        } catch (Exception e) {
            log.error("获取BitMap位失败: key={}, bitIndex={}", bitSetKey, bitIndex, e);
            throw e;
        }
    }

    /**
     * 获取BitMap中1的个数
     */
    public long getBitCount(String bitSetKey) {
        try {
            return redissonClient.getBitSet(bitSetKey).cardinality();
        } catch (Exception e) {
            log.error("获取BitMap中1的个数失败: key={}", bitSetKey, e);
            throw e;
        }
    }

    /**
     * HyperLogLog操作
     */
    public RHyperLogLog<String> getHyperLogLog(String hyperLogLogKey) {
        return redissonClient.getHyperLogLog(hyperLogLogKey);
    }

    /**
     * 添加元素到HyperLogLog
     */
    public boolean addToHyperLogLog(String hyperLogLogKey, String... values) {
        try {
            RHyperLogLog<String> hyperLogLog = redissonClient.getHyperLogLog(hyperLogLogKey);
            return hyperLogLog.addAll(Arrays.asList(values));
        } catch (Exception e) {
            log.error("添加元素到HyperLogLog失败: key={}, values={}", hyperLogLogKey, Arrays.toString(values), e);
            throw e;
        }
    }

    /**
     * 获取HyperLogLog基数
     */
    public long getHyperLogLogCount(String hyperLogLogKey) {
        try {
            return redissonClient.getHyperLogLog(hyperLogLogKey).count();
        } catch (Exception e) {
            log.error("获取HyperLogLog基数失败: key={}", hyperLogLogKey, e);
            throw e;
        }
    }

    // ==================== 通用操作 ====================

    /**
     * 删除键
     */
    public boolean delete(String key) {
        try {
            return redissonClient.getKeys().delete(key) > 0;
        } catch (Exception e) {
            log.error("删除键失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 批量删除键
     */
    public long deleteKeys(String... keys) {
        try {
            return redissonClient.getKeys().delete(keys);
        } catch (Exception e) {
            log.error("批量删除键失败: keys={}", Arrays.toString(keys), e);
            throw e;
        }
    }

    /**
     * 检查键是否存在
     */
    public boolean exists(String key) {
        try {
            return redissonClient.getKeys().countExists(key) > 0;
        } catch (Exception e) {
            log.error("检查键存在失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 获取键类型
     */
    public String getType(String key) {
        try {
            return redissonClient.getKeys().getType(key).name();
        } catch (Exception e) {
            log.error("获取键类型失败: key={}", key, e);
            throw e;
        }
    }

    /**
     * 获取键数量
     */
    public long getKeysCount() {
        try {
            return redissonClient.getKeys().count();
        } catch (Exception e) {
            log.error("获取键数量失败", e);
            throw e;
        }
    }

    /**
     * 清空当前数据库
     */
    public void flushDB() {
        try {
            redissonClient.getKeys().flushdb();
        } catch (Exception e) {
            log.error("清空数据库失败", e);
            throw e;
        }
    }

    /**
     * 清空所有数据库
     */
    public void flushAll() {
        try {
            redissonClient.getKeys().flushall();
        } catch (Exception e) {
            log.error("清空所有数据库失败", e);
            throw e;
        }
    }

    // ==================== Lua脚本执行 ====================

    /**
     * 执行Lua脚本
     * @param script Lua脚本内容
     * @param keys 脚本中使用的key列表
     * @param args 脚本参数
     * @return 执行结果
     */
    public Object executeLuaScript(String script, List<String> keys, Object... args) {
        try {
            List<Object> keyObjects = new ArrayList<>(keys);
            return redissonClient.getScript().eval(org.redisson.api.RScript.Mode.READ_WRITE, script, 
                    org.redisson.api.RScript.ReturnType.VALUE, keyObjects, args);
        } catch (Exception e) {
            log.error("执行Lua脚本失败: script={}, keys={}, args={}", script, keys, Arrays.toString(args), e);
            throw e;
        }
    }

    public Long executeLuaScriptAsLong(String script, List<String> keys, Object... args) {
        try {
            List<Object> keyObjects = new ArrayList<>(keys);
            return redissonClient.getScript().eval(org.redisson.api.RScript.Mode.READ_WRITE, script,
                    org.redisson.api.RScript.ReturnType.INTEGER, keyObjects, args);
        } catch (Exception e) {
            log.error("执行Lua脚本失败: script={}, keys={}, args={}", script, keys, Arrays.toString(args), e);
            throw e;
        }
    }


    /**
     * 执行Lua脚本（返回Long类型，使用StringCodec）
     * 适合传递字符串参数，性能优于JsonJacksonCodec（无需JSON序列化/反序列化）
     * @param script Lua脚本内容
     * @param keys 脚本中使用的key列表
     * @param args 脚本参数（建议传递字符串）
     * @return 执行结果
     */
    public Long executeLuaScriptAsLongUseStringCodec(String script, List<String> keys, Object... args) {
        try {
            List<Object> keyObjects = new ArrayList<>(keys);
            // 使用StringCodec，参数直接作为字符串传递，无需JSON序列化
            // Lua脚本中直接使用ARGV[1]，不需要cjson.decode
            return redissonClient.getScript(org.redisson.client.codec.StringCodec.INSTANCE).eval(
                    org.redisson.api.RScript.Mode.READ_WRITE, script, 
                    org.redisson.api.RScript.ReturnType.INTEGER, keyObjects, args);
        } catch (Exception e) {
            log.error("执行Lua脚本失败(StringCodec): script={}, keys={}, args={}", script, keys, Arrays.toString(args), e);
            throw e;
        }
    }

    /**
     * 执行Lua脚本（返回Long类型）
     * 使用JsonJacksonCodec确保参数正确序列化为JSON格式，Lua脚本可以正常处理
     * @param script Lua脚本内容
     * @param keys 脚本中使用的key列表
     * @param args 脚本参数
     * @return 执行结果
     */
    public Long executeLuaScriptAsLongUseJsonJacksonCodec(String script, List<String> keys, Object... args) {
        try {
            List<Object> keyObjects = new ArrayList<>(keys);
            // 使用JsonJacksonCodec替代默认的MarshallingCodec
            // 这样Lua脚本可以正常处理JSON字符串参数
            return redissonClient.getScript(org.redisson.codec.JsonJacksonCodec.INSTANCE).eval(
                    org.redisson.api.RScript.Mode.READ_WRITE, script, 
                    org.redisson.api.RScript.ReturnType.INTEGER, keyObjects, args);
        } catch (Exception e) {
            log.error("执行Lua脚本失败: script={}, keys={}, args={}", script, keys, Arrays.toString(args), e);
            throw e;
        }
    }

    /**
     * 执行Lua脚本（使用StringCodec，返回Long类型）
     * 使用StringCodec确保所有参数都以字符串形式传递到Lua脚本
     * @param script Lua脚本内容
     * @param keys 脚本中使用的key列表
     * @param args 脚本参数（建议全部使用String类型）
     * @return 执行结果
     */
    public Long executeLuaScriptAsLongWithStringCodec(String script, List<String> keys, Object... args) {
        try {
            List<Object> keyObjects = new ArrayList<>(keys);
            return redissonClient.getScript(org.redisson.client.codec.StringCodec.INSTANCE)
                    .eval(org.redisson.api.RScript.Mode.READ_WRITE, script, 
                    org.redisson.api.RScript.ReturnType.INTEGER, keyObjects, args);
        } catch (Exception e) {
            log.error("执行Lua脚本失败(StringCodec): script={}, keys={}, args={}", script, keys, Arrays.toString(args), e);
            throw e;
        }
    }
} 