package io.wexchain.dcc.marketing.ext.service.helper;

import org.dozer.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.wexmarket.topia.commons.data.rpc.ResponseHelper;

import io.wexchain.dcc.marketing.domain.Candy;

@Component
public class CandyResponseHelper extends ResponseHelper<Candy, io.wexchain.dcc.marketing.api.model.candy.Candy> {

	{
		setModelClass(io.wexchain.dcc.marketing.api.model.candy.Candy.class);
	}

	@Autowired
	@Qualifier("dozerBeanMapper")
	@Override
	public void setMapper(Mapper mapper) {
		super.setMapper(mapper);
	}

}
