package com.redhat.customer.translate;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;

import com.sun.mdm.index.webservice.ExecuteMatchUpdate;

@Converter
public class TransformToParams {

	@Converter
	public List convertTo(Object value, Exchange exchange) throws TypeConversionException {

		ExecuteMatchUpdate obj = (ExecuteMatchUpdate) value;
		List params = new ArrayList();
		params.add(obj.getCallerInfo());
		params.add(obj.getSysObjBean());

		if (exchange != null) {
			exchange.getOut().setBody(params);
		}

		return params;
	}

}
