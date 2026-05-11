package com.haalan.seckill.controller;


import com.haalan.common.domain.R;
import com.haalan.common.utils.UserContext;
import com.haalan.seckill.domain.dto.SeckillTokenRequestDTO;
import com.haalan.seckill.domain.vo.SeckillTokenVO;
import com.haalan.seckill.service.ITSeckillTokenService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
@Api(tags = "秒杀令牌管理")
public class SeckillTokenController {

	private final ITSeckillTokenService seckillTokenService;

	@PostMapping("/token")
	@ApiOperation("生成秒杀令牌")
	public R<SeckillTokenVO> generateToken(@RequestBody @Validated SeckillTokenRequestDTO requestDTO) {
		Long userId = UserContext.getUser();

		SeckillTokenVO tokenVO = seckillTokenService.generateToken(userId, requestDTO);

		return R.success("令牌生成成功", tokenVO);
	}
}
