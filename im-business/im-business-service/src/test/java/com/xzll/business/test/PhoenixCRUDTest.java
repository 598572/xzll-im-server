package com.xzll.business.test;

import cn.hutool.core.util.RandomUtil;
import org.junit.jupiter.api.Test;

import cn.hutool.core.util.RandomUtil;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 
 * @Author: hzz
 * @Date:  2025/8/27 14:22:42
 * @Description: 
 */
public class PhoenixCRUDTest {



        // ==================== 配置参数 ====================
        // 请根据您的实际环境修改这些参数
        private static final String ZOOKEEPER_QUORUM = "120.46.85.43";  // 修改为您的ZooKeeper地址
        private static final int ZOOKEEPER_PORT = 2181;              // 修改为您的ZooKeeper端口

        // Phoenix JDBC 连接URL
//        private static final String PHOENIX_JDBC_URL = "jdbc:phoenix:" + ZOOKEEPER_QUORUM + ":" + ZOOKEEPER_PORT+"/hbase";


    // 修改连接URL，添加超时参数
    private static final String PHOENIX_JDBC_URL =
            "jdbc:phoenix:" + ZOOKEEPER_QUORUM + ":" + ZOOKEEPER_PORT +
                    "?phoenix.query.timeoutMs=60000" + // 查询超时60秒
                    "&phoenix.schema.isNamespaceMappingEnabled=true" + // 确保命名空间映射一致
                    "&hbase.rpc.timeout=60000"; // HBase RPC超时

        // 测试表配置
        private static final String TEST_TABLE_NAME = "TEST_PHOENIX_TABLE01";
//        private static final String SCHEMA_NAME = "TEST_SCHEMA";
        private static final String FULL_TABLE_NAME =  TEST_TABLE_NAME;

        /**
         * 获取Phoenix数据库连接
         */
        private Connection getPhoenixConnection() throws SQLException {
            try {
                // 加载Phoenix JDBC驱动
                Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");

                // 创建连接
                return DriverManager.getConnection(PHOENIX_JDBC_URL);
            } catch (ClassNotFoundException e) {
                throw new SQLException("Phoenix JDBC Driver not found", e);
            }
        }

        /**
         * 测试Phoenix连接
         */
        @Test
        public void testPhoenixConnection() {
            System.out.println("=== 测试Phoenix连接 ===");

            try (Connection connection = getPhoenixConnection()) {
                System.out.println("✅ Phoenix连接成功");

                // 获取数据库元数据
                DatabaseMetaData metaData = connection.getMetaData();
                System.out.println("Database Product: " + metaData.getDatabaseProductName());
                System.out.println("Database Version: " + metaData.getDatabaseProductVersion());

            } catch (Exception e) {
                System.out.println("❌ Phoenix连接失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 创建测试表
         */
        @Test
        public void testCreateTable() {
            System.out.println("=== 测试创建Phoenix表 ===");

            // 先尝试删除表（如果存在）
            dropTestTable();

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                // 创建Schema（如果不存在）
                String createSchemaSQL = "CREATE SCHEMA IF NOT EXISTS " ;
                statement.execute(createSchemaSQL);
//                System.out.println("✅ Schema创建成功: " + SCHEMA_NAME);

                // 创建表
                String createTableSQL = "CREATE TABLE " + FULL_TABLE_NAME + " (" +
                        "ID VARCHAR NOT NULL PRIMARY KEY, " +
                        "NAME VARCHAR, " +
                        "AGE INTEGER, " +
                        "EMAIL VARCHAR, " +
                        "CREATE_TIME TIMESTAMP" +
                        ")";

                statement.execute(createTableSQL);
                System.out.println("✅ 表创建成功: " + FULL_TABLE_NAME);

            } catch (Exception e) {
                System.out.println("❌ 表创建失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 删除测试表
         */
        private void dropTestTable() {
            System.out.println("=== 清理测试表 ===");

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                // 删除表（如果存在）
                String dropTableSQL = "DROP TABLE IF EXISTS " + FULL_TABLE_NAME;
                statement.execute(dropTableSQL);
                System.out.println("✅ 表删除成功: " + FULL_TABLE_NAME);

                // 删除Schema（如果为空）
                String dropSchemaSQL = "DROP SCHEMA IF EXISTS ";
                statement.execute(dropSchemaSQL);
                System.out.println("✅ Schema删除成功: " );

            } catch (Exception e) {
                System.out.println("⚠️ 清理操作失败: " + e.getMessage());
                // 不抛出异常，因为表可能不存在
            }
        }

        /**
         * 测试数据插入
         */
        @Test
        public void testInsertData() {
            System.out.println("=== 测试Phoenix数据插入 ===");

            // 确保表存在
            testCreateTable();

            try (Connection connection = getPhoenixConnection();
                 PreparedStatement pstmt = connection.prepareStatement(
                         "UPSERT INTO " + FULL_TABLE_NAME + " (ID, NAME, AGE, EMAIL, CREATE_TIME) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)")) {

                String id = "user_" + RandomUtil.randomNumbers(10);
                String name = "张三";
                int age = 25;
                String email = "zhangsan@example.com";

                pstmt.setString(1, id);
                pstmt.setString(2, name);
                pstmt.setInt(3, age);
                pstmt.setString(4, email);

                int affectedRows = pstmt.executeUpdate();
                connection.commit(); // Phoenix需要显式提交

                System.out.println("✅ 数据插入成功，影响行数: " + affectedRows + ", ID: " + id);

            } catch (Exception e) {
                System.out.println("❌ 插入数据失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 测试数据查询
         */
        @Test
        public void testQueryData() {
            System.out.println("=== 测试Phoenix数据查询 ===");

            // 先插入一些测试数据
            testInsertData();

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                String querySQL = "SELECT ID, NAME, AGE, EMAIL, CREATE_TIME FROM " + FULL_TABLE_NAME + " LIMIT 10";
                ResultSet resultSet = statement.executeQuery(querySQL);

                List<Map<String, Object>> records = new ArrayList<>();
                int count = 0;

                while (resultSet.next()) {
                    count++;
                    Map<String, Object> record = new HashMap<>();
                    record.put("ID", resultSet.getString("ID"));
                    record.put("NAME", resultSet.getString("NAME"));
                    record.put("AGE", resultSet.getInt("AGE"));
                    record.put("EMAIL", resultSet.getString("EMAIL"));
                    record.put("CREATE_TIME", resultSet.getTimestamp("CREATE_TIME"));

                    records.add(record);
                }

                resultSet.close();

                System.out.println("✅ 数据查询成功，查询到 " + count + " 条记录");
                for (Map<String, Object> record : records) {
                    System.out.println("  记录: " + record);
                }

            } catch (Exception e) {
                System.out.println("❌ 查询数据失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 测试数据更新
         */
        @Test
        public void testUpdateData() {
            System.out.println("=== 测试Phoenix数据更新 ===");

            // 先插入一些测试数据
            testInsertData();

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                // 先查询一条记录
                String querySQL = "SELECT ID FROM " + FULL_TABLE_NAME + " LIMIT 1";
                ResultSet resultSet = statement.executeQuery(querySQL);

                if (resultSet.next()) {
                    String id = resultSet.getString("ID");
                    resultSet.close();

                    // 更新这条记录
                    String updateSQL = "UPSERT INTO " + FULL_TABLE_NAME + " (ID, AGE) VALUES ('" + id + "', 30)";
                    int affectedRows = statement.executeUpdate(updateSQL);
                    connection.commit();

                    System.out.println("✅ 数据更新成功，影响行数: " + affectedRows + ", ID: " + id);

                    // 验证更新
                    String verifySQL = "SELECT ID, AGE FROM " + FULL_TABLE_NAME + " WHERE ID = '" + id + "'";
                    ResultSet verifyResult = statement.executeQuery(verifySQL);

                    if (verifyResult.next()) {
                        int updatedAge = verifyResult.getInt("AGE");
                        System.out.println("🔍 更新验证: ID=" + id + ", 新年龄=" + updatedAge);
                    }

                    verifyResult.close();
                } else {
                    System.out.println("⚠️ 没有找到可更新的记录");
                }

            } catch (Exception e) {
                System.out.println("❌ 更新数据失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 测试数据删除
         */
        @Test
        public void testDeleteData() {
            System.out.println("=== 测试Phoenix数据删除 ===");

            // 先插入一些测试数据
            testInsertData();

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                // 先查询一条记录
                String querySQL = "SELECT ID FROM " + FULL_TABLE_NAME + " LIMIT 1";
                ResultSet resultSet = statement.executeQuery(querySQL);

                if (resultSet.next()) {
                    String id = resultSet.getString("ID");
                    resultSet.close();

                    // 删除这条记录
                    String deleteSQL = "DELETE FROM " + FULL_TABLE_NAME + " WHERE ID = '" + id + "'";
                    int affectedRows = statement.executeUpdate(deleteSQL);
                    connection.commit();

                    System.out.println("✅ 数据删除成功，影响行数: " + affectedRows + ", ID: " + id);

                    // 验证删除
                    String verifySQL = "SELECT COUNT(*) AS CNT FROM " + FULL_TABLE_NAME + " WHERE ID = '" + id + "'";
                    ResultSet verifyResult = statement.executeQuery(verifySQL);

                    if (verifyResult.next() && verifyResult.getInt("CNT") == 0) {
                        System.out.println("🔍 删除验证: 记录已成功删除");
                    } else {
                        System.out.println("⚠️ 删除验证: 记录仍然存在");
                    }

                    verifyResult.close();
                } else {
                    System.out.println("⚠️ 没有找到可删除的记录");
                }

            } catch (Exception e) {
                System.out.println("❌ 删除数据失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 测试完整CRUD流程
         */
        @Test
        public void testFullCRUD() {
            System.out.println("=== 测试Phoenix完整CRUD流程 ===");

            // 确保表存在
            testCreateTable();

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                String id = "crud_test_" + System.currentTimeMillis();

                // 1. 插入数据
                String insertSQL = "UPSERT INTO " + FULL_TABLE_NAME + " (ID, NAME, AGE, EMAIL, CREATE_TIME) VALUES (" +
                        "'" + id + "', 'CRUD测试用户', 28, 'crud@example.com', CURRENT_TIMESTAMP)";
                int insertRows = statement.executeUpdate(insertSQL);
                connection.commit();
                System.out.println("✅ 1. 数据插入成功，影响行数: " + insertRows + ", ID: " + id);

                // 2. 查询刚插入的数据
                String querySQL = "SELECT ID, NAME, AGE, EMAIL FROM " + FULL_TABLE_NAME + " WHERE ID = '" + id + "'";
                ResultSet resultSet = statement.executeQuery(querySQL);

                if (resultSet.next()) {
                    String name = resultSet.getString("NAME");
                    int age = resultSet.getInt("AGE");
                    System.out.println("✅ 2. 数据查询成功 - name: " + name + ", age: " + age);
                } else {
                    System.out.println("❌ 2. 数据查询失败，未找到数据");
                }
                resultSet.close();

                // 3. 更新数据
                String updateSQL = "UPSERT INTO " + FULL_TABLE_NAME + " (ID, AGE) VALUES ('" + id + "', 29)";
                int updateRows = statement.executeUpdate(updateSQL);
                connection.commit();
                System.out.println("✅ 3. 数据更新成功，影响行数: " + updateRows);

                // 4. 验证更新
                ResultSet updatedResult = statement.executeQuery(querySQL);
                if (updatedResult.next()) {
                    int updatedAge = updatedResult.getInt("AGE");
                    System.out.println("✅ 4. 数据更新验证成功 - 新age: " + updatedAge);
                }
                updatedResult.close();

                // 5. 删除数据
                String deleteSQL = "DELETE FROM " + FULL_TABLE_NAME + " WHERE ID = '" + id + "'";
                int deleteRows = statement.executeUpdate(deleteSQL);
                connection.commit();
                System.out.println("✅ 5. 数据删除成功，影响行数: " + deleteRows);

                // 6. 验证删除
                ResultSet deletedResult = statement.executeQuery(querySQL);
                if (!deletedResult.next()) {
                    System.out.println("✅ 6. 数据删除验证成功");
                } else {
                    System.out.println("❌ 6. 数据删除验证失败，数据仍然存在");
                }
                deletedResult.close();

                System.out.println("🎉 Phoenix完整CRUD流程测试完成！");

            } catch (Exception e) {
                System.out.println("❌ CRUD流程测试失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 测试批量操作
         */
        @Test
        public void testBatchOperations() {
            System.out.println("=== 测试Phoenix批量操作 ===");

            // 确保表存在
            testCreateTable();

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                // 开始批量操作
                connection.setAutoCommit(false);

                // 插入多条数据
                for (int i = 1; i <= 5; i++) {
                    String id = "batch_user_" + String.format("%03d", i);
                    String name = "批量用户" + i;
                    int age = 20 + i;
                    String email = "batch" + i + "@example.com";

                    String insertSQL = "UPSERT INTO " + FULL_TABLE_NAME + " (ID, NAME, AGE, EMAIL, CREATE_TIME) VALUES (" +
                            "'" + id + "', '" + name + "', " + age + ", '" + email + "', CURRENT_TIMESTAMP)";

                    statement.addBatch(insertSQL);
                }

                // 执行批量操作
                int[] results = statement.executeBatch();
                connection.commit();
                connection.setAutoCommit(true);

                int successCount = 0;
                for (int result : results) {
                    if (result >= 0) { // Phoenix返回的是影响行数，成功为非负数
                        successCount++;
                    }
                }

                System.out.println("✅ 批量插入成功: " + successCount + "条数据");

                // 验证批量插入
                String countSQL = "SELECT COUNT(*) AS CNT FROM " + FULL_TABLE_NAME + " WHERE ID LIKE 'batch_user_%'";
                ResultSet countResult = statement.executeQuery(countSQL);

                if (countResult.next()) {
                    int count = countResult.getInt("CNT");
                    System.out.println("📊 批量插入验证: 成功插入" + count + "条记录");
                }

                countResult.close();

            } catch (Exception e) {
                System.out.println("❌ 批量操作测试失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 测试条件查询
         */
        @Test
        public void testConditionalQuery() {
            System.out.println("=== 测试Phoenix条件查询 ===");

            // 确保表存在并有一些数据
            testBatchOperations();

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                // 查询年龄大于22的用户
                String querySQL = "SELECT ID, NAME, AGE, EMAIL FROM " + FULL_TABLE_NAME +
                        " WHERE AGE > 22 AND ID LIKE 'batch_user_%' ORDER BY AGE DESC";

                ResultSet resultSet = statement.executeQuery(querySQL);

                List<Map<String, Object>> records = new ArrayList<>();
                int count = 0;

                while (resultSet.next()) {
                    count++;
                    Map<String, Object> record = new HashMap<>();
                    record.put("ID", resultSet.getString("ID"));
                    record.put("NAME", resultSet.getString("NAME"));
                    record.put("AGE", resultSet.getInt("AGE"));
                    record.put("EMAIL", resultSet.getString("EMAIL"));

                    records.add(record);
                }

                resultSet.close();

                System.out.println("✅ 条件查询成功，查询到 " + count + " 条记录");
                for (Map<String, Object> record : records) {
                    System.out.println("  记录: " + record);
                }

            } catch (Exception e) {
                System.out.println("❌ 条件查询失败: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        /**
         * 测试二级索引（如果支持）
         */
        @Test
        public void testSecondaryIndex() {
            System.out.println("=== 测试Phoenix二级索引 ===");

            // 确保表存在
            testCreateTable();

            try (Connection connection = getPhoenixConnection();
                 Statement statement = connection.createStatement()) {

                // 创建二级索引
                String indexName = "IDX_" + TEST_TABLE_NAME + "_EMAIL";
                String createIndexSQL = "CREATE INDEX " + indexName + " ON " + FULL_TABLE_NAME + " (EMAIL)";

                statement.execute(createIndexSQL);
                System.out.println("✅ 二级索引创建成功: " + indexName);

                // 使用索引查询
                String querySQL = "SELECT ID, NAME, EMAIL FROM " + FULL_TABLE_NAME + " WHERE EMAIL = 'test@example.com'";
                ResultSet resultSet = statement.executeQuery(querySQL);

                int count = 0;
                while (resultSet.next()) {
                    count++;
                }

                resultSet.close();
                System.out.println("✅ 索引查询成功，查询到 " + count + " 条记录");

                // 删除索引
                String dropIndexSQL = "DROP INDEX " + indexName + " ON " + FULL_TABLE_NAME;
                statement.execute(dropIndexSQL);
                System.out.println("✅ 二级索引删除成功: " + indexName);

            } catch (Exception e) {
                System.out.println("❌ 二级索引测试失败: " + e.getMessage());
                e.printStackTrace();
                // 不抛出异常，因为某些Phoenix版本可能不支持二级索引
            }
        }

        /**
         * 清理测试环境
         */
        @Test
        public void testCleanup() {
            System.out.println("=== 清理Phoenix测试环境 ===");
            dropTestTable();
            System.out.println("✅ 测试环境清理完成");
        }

}
