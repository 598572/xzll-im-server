package com.xzll.business.controller;

import com.alibaba.excel.EasyExcel;
import com.google.common.collect.Lists;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * @Auther: Huangzhuangzhuang
 * @Date: 2021/5/22 18:10
 * @Description:
 */
@Slf4j
@RestController
@RequestMapping("/make/data")
public class TestController {

	@Value("${timeOutConfig}")
	private Long timeOutConfig;
	@Resource
	private ImC2CMsgRecordHBaseService imC2CMsgRecordService;

	@GetMapping("/get")
	public Long get() {
		log.info("测试nacos动态刷新，timeOutConfig：{}",timeOutConfig);
		return timeOutConfig;
	}


	@PostMapping("/make1")
	public List<UserData> makeData() {
		UserData user = new UserData(
				"黄飞鸿",
				"30", // Age
				"18211068752", // Phone
				"北京市朝阳区大屯路东", // Address
				"h163361361@163.com", // Email
				"6222 0000 0000 0000 000", // Bank Account
				"123456kkm" // Password
		);

		UserData user2 = new UserData(
				"黄飞鸿02",
				"20", // Age
				"15729209087", // Phone
				"北京市朝阳区国际会展中心", // Address
				"h13333333@163.com", // Email
				"6222 0000 0000 0000 000", // Bank Account
				"mmmnnndddd" // Password
		);

		List<UserData> objects = Lists.newArrayList();
		objects.add(user);
		objects.add(user2);
		return objects;
	}

	/**
	 * 测试es读写
	 *
	 * @param dto
	 * @return
	 */
	@PostMapping("/testes")
	public Long testes(@RequestBody C2CSendMsgAO dto) {
		imC2CMsgRecordService.testEs(dto);
		return timeOutConfig;
	}

	@GetMapping("/downloadExcelData")
	public ResponseEntity<byte[]> downloadExcelData() throws IOException {
		// 假设通过 makeData() 方法获取到用户数据列表
		List<UserData> userList = makeData2();

		// 使用 ByteArrayOutputStream 来创建 Excel 文件内容
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

		// 使用 EasyExcel 将数据写入 Excel
		EasyExcel.write(byteArrayOutputStream, UserData.class)
				.sheet("User Data") // 设置 Excel 工作表名称
				.doWrite(userList);

		// 设置文件名
		String fileName = "user_data.xlsx";

		// 返回 Excel 文件的响应
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(byteArrayOutputStream.toByteArray());
	}

	// 生成 UserData 数据的示例方法
	public List<UserData> makeData2() {
		UserData user = new UserData(
				"黄飞鸿",
				"30", // Age
				"18211068752", // Phone
				"北京市朝阳区大屯路东", // Address
				"h163361361@163.com", // Email
				"6222 0000 0000 0000 000", // Bank Account
				"123456kkm" // Password
		);

		UserData user2 = new UserData(
				"黄飞鸿02",
				"20", // Age
				"15729209087", // Phone
				"北京市朝阳区国际会展中心", // Address
				"h13333333@163.com", // Email
				"6222 0000 0000 0000 000", // Bank Account
				"mmmnnndddd" // Password
		);

		return Lists.newArrayList(user, user2);
	}


}
