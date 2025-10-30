package com.xzll.auth.service.impl;

import cn.hutool.core.collection.CollUtil;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Lists;
import com.xzll.auth.constant.AuthConstant;
import com.xzll.auth.constant.MessageConstant;
import com.xzll.auth.domain.SecurityUser;
import com.xzll.auth.domain.UserDTO;
import com.xzll.auth.mapper.ImUserMapper;
import com.xzll.auth.service.UserService;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.pojo.entity.ImUserDO;
import com.xzll.common.util.JsonUtils;
import com.xzll.common.util.msgId.SnowflakeIdService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;


/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: 用户管理业务类
 */
@Slf4j
@Service
public class UserServiceImpl implements UserDetailsService, UserService {

    private List<UserDTO> userList;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ImUserMapper imUserMapper;
    
    @Resource
    private SnowflakeIdService snowflakeIdService;

    // 用户名正则表达式：4-20位字母数字下划线
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{" + AuthConstant.USERNAME_MIN_LENGTH + "," + AuthConstant.USERNAME_MAX_LENGTH + "}$");
    // 密码正则表达式：至少8位，包含字母和数字
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{" + AuthConstant.PASSWORD_MIN_LENGTH + ",}$");

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("加载用户信息入参:{}", username);
        
        // 参数验证
        if (StringUtils.isBlank(username)) {
            log.error("用户名为空");
            throw new UsernameNotFoundException("用户名不能为空");
        }
        
        ImUserDO imUser = imUserMapper.selectOne(new QueryWrapper<ImUserDO>().eq("user_name", username));
        // 如果没有找到用户，抛出异常
        if (imUser == null) {
            log.warn("用户不存在，用户名: {}", username);
            throw new UsernameNotFoundException(MessageConstant.USERNAME_NOT_EXIST);
        }
        
        // 数据完整性验证
        if (StringUtils.isBlank(imUser.getUserId())) {
            log.error("用户ID为空，用户名: {}", username);
            throw new RuntimeException("用户数据异常：用户ID为空");
        }
        
        if (StringUtils.isBlank(imUser.getPassword())) {
            log.error("用户密码为空，用户名: {}", username);
            throw new RuntimeException("用户数据异常：密码为空");
        }
        
        UserDTO userDTO = new UserDTO();
        try {
            // 设置用户ID - 这是关键修复
            userDTO.setId(Long.parseLong(imUser.getUserId()));
        } catch (NumberFormatException e) {
            log.error("用户ID格式错误，userId: {}, 用户名: {}", imUser.getUserId(), username);
            throw new RuntimeException("用户数据异常：用户ID格式错误");
        }
        
        userDTO.setUserName(imUser.getUserName());
        userDTO.setPassword(imUser.getPassword());
        userDTO.setStatus(AuthConstant.DEFAULT_USER_STATUS);
        userDTO.setRoles(CollUtil.toList(AuthConstant.DEFAULT_USER_ROLE));
        
        SecurityUser securityUser = new SecurityUser(userDTO);
        
        // 用户状态验证
        if (!securityUser.isEnabled()) {
            log.warn("用户账户已禁用，用户名: {}", username);
            throw new DisabledException(MessageConstant.ACCOUNT_DISABLED);
        } else if (!securityUser.isAccountNonLocked()) {
            log.warn("用户账户已锁定，用户名: {}", username);
            throw new LockedException(MessageConstant.ACCOUNT_LOCKED);
        } else if (!securityUser.isAccountNonExpired()) {
            log.warn("用户账户已过期，用户名: {}", username);
            throw new AccountExpiredException(MessageConstant.ACCOUNT_EXPIRED);
        } else if (!securityUser.isCredentialsNonExpired()) {
            log.warn("用户凭证已过期，用户名: {}", username);
            throw new CredentialsExpiredException(MessageConstant.CREDENTIALS_EXPIRED);
        }
        
        log.debug("用户信息加载成功，用户名: {}, 用户ID: {}", username, userDTO.getId());
        return securityUser;
    }


    /**
     * 用户注册
     *
     * @param userDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerUser(UserDTO userDTO) {
        try {
            // 1. 验证用户输入
            validateUserInput(userDTO);
            
            // 2. 验证用户名是否已存在
            Assert.isTrue(!checkUserNameExists(userDTO.getUserName()), AnswerCode.USER_EXIST.getMessage());

            // 3. 创建新用户并加密密码
            ImUserDO user = createUserFromDTO(userDTO);
            
            // 4. 保存用户
            int row = imUserMapper.insert(user);
            log.info("注册用户成功，row:{},user:{}", row, JsonUtils.toJsonStr(user));
            return row == 1;
        } catch (Exception e) {
            log.error("用户注册失败，userDTO:{}", JsonUtils.toJsonStr(userDTO), e);
            throw e;
        }
    }
    
    /**
     * 验证用户输入
     */
    private void validateUserInput(UserDTO userDTO) {
        // 验证用户名
        Assert.isTrue(StringUtils.isNotBlank(userDTO.getUserName()), "用户名不能为空");
        Assert.isTrue(USERNAME_PATTERN.matcher(userDTO.getUserName()).matches(), 
            "用户名格式不正确，应为4-20位字母数字下划线");
        
        // 验证密码
        Assert.isTrue(StringUtils.isNotBlank(userDTO.getPassword()), "密码不能为空");
        Assert.isTrue(PASSWORD_PATTERN.matcher(userDTO.getPassword()).matches(), 
            "密码格式不正确，至少8位，包含字母和数字");
    }
    
    /**
     * 从DTO创建用户实体
     */
    private ImUserDO createUserFromDTO(UserDTO userDTO) {
        ImUserDO user = new ImUserDO();
        BeanUtils.copyProperties(userDTO, user);

        // 生成用户ID - 使用压缩雪花算法（10-12位数字，比原版减少约40%）
        user.setUserId(snowflakeIdService.generateCompactUserId());
        
        // 加密密码
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        
        // 设置时间
        LocalDateTime now = LocalDateTime.now();
        user.setRegisterTime(now);
        user.setLastLoginTime(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        
        // 设置默认值
        if (user.getSex() == null) {
            user.setSex(AuthConstant.DEFAULT_SEX); // 未知性别
        }
        if (user.getRegisterTerminalType() == null) {
            user.setRegisterTerminalType(AuthConstant.DEFAULT_REGISTER_TERMINAL_TYPE); // 默认web端
        }
        
        return user;
    }

    /**
     * 检查用户名是否已存在,存在返回true 不存在返回false
     */
    protected boolean checkUserNameExists(String username) {
        LambdaQueryWrapper<ImUserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ImUserDO::getUserName, username);
        return imUserMapper.exists(queryWrapper);
    }

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return UserDTO
     */
    @Override
    public UserDTO getUserById(String userId) {
        ImUserDO imUser = imUserMapper.selectById(userId);
        if (imUser == null) {
            return null;
        }
        return convertToUserDTO(imUser);
    }

    /**
     * 更新用户信息
     *
     * @param userDTO 用户信息
     * @return 是否更新成功
     */
    @Override
    public boolean updateUser(UserDTO userDTO) {
        Assert.notNull(userDTO.getId(), AnswerCode.PARAMETER_ERROR.getMessage());
        
        ImUserDO imUser = imUserMapper.selectById(userDTO.getId());
        if (imUser == null) {
            return false;
        }

        ImUserDO updateUser = new ImUserDO();
        updateUser.setUserId(imUser.getUserId());
        if (StringUtils.isNotBlank(userDTO.getUserName())) {
            updateUser.setUserName(userDTO.getUserName());
        }
        if (StringUtils.isNotBlank(userDTO.getPassword())) {
            updateUser.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        updateUser.setUpdateTime(LocalDateTime.now());
        
        int row = imUserMapper.updateById(updateUser);
        return row == 1;
    }

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    @Override
    public boolean deleteUser(String userId) {
        Assert.notNull(userId, AnswerCode.PARAMETER_ERROR.getMessage());
        int row = imUserMapper.deleteById(userId);
        return row == 1;
    }

    /**
     * 分页查询用户列表
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param username 用户名（可选）
     * @return 用户列表
     */
    @Override
    public List<UserDTO> listUsers(Integer pageNum, Integer pageSize, String username) {
        LambdaQueryWrapper<ImUserDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like(ImUserDO::getUserName, username);
        }
        queryWrapper.orderByDesc(ImUserDO::getCreateTime);
        
        List<ImUserDO> userList = imUserMapper.selectList(queryWrapper);
        return userList.stream()
                .map(this::convertToUserDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 修改用户密码
     *
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否修改成功
     */
    @Override
    public boolean updatePassword(String userId, String oldPassword, String newPassword) {
        Assert.isTrue(StringUtils.isNotBlank(oldPassword) && StringUtils.isNotBlank(newPassword), 
            AnswerCode.PARAMETER_ERROR.getMessage());

        ImUserDO user = imUserMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("旧密码不正确");
        }

        // 更新新密码
        ImUserDO updateUser = new ImUserDO();
        updateUser.setUserId(userId);
        updateUser.setPassword(passwordEncoder.encode(newPassword));
        updateUser.setUpdateTime(LocalDateTime.now());

        int row = imUserMapper.updateById(updateUser);
        return row == 1;
    }

    /**
     * 重置用户密码（管理员功能）
     *
     * @param userId 用户ID
     * @param newPassword 新密码
     * @return 是否重置成功
     */
    @Override
    public boolean resetPassword(String userId, String newPassword) {
        Assert.isTrue(StringUtils.isNotBlank(newPassword), AnswerCode.PARAMETER_ERROR.getMessage());

        ImUserDO user = imUserMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        // 验证新密码格式
        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new RuntimeException("密码格式不正确，至少8位，包含字母和数字");
        }

        // 更新新密码
        ImUserDO updateUser = new ImUserDO();
        updateUser.setUserId(userId);
        updateUser.setPassword(passwordEncoder.encode(newPassword));
        updateUser.setUpdateTime(LocalDateTime.now());

        int row = imUserMapper.updateById(updateUser);
        log.info("管理员重置用户密码成功，用户ID: {}", userId);
        return row == 1;
    }

    /**
     * 将 ImUserDO 转换为 UserDTO
     */
    private UserDTO convertToUserDTO(ImUserDO imUser) {
        if (imUser == null) {
            return null;
        }
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.parseLong(imUser.getUserId()));
        userDTO.setUserName(imUser.getUserName());
        userDTO.setPassword(imUser.getPassword());
        userDTO.setStatus(1);
        userDTO.setRoles(Lists.newArrayList());
        return userDTO;
    }

}
