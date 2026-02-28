package com.bub6le.crackserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bub6le.crackserver.entity.UserToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserTokenMapper extends BaseMapper<UserToken> {
}
