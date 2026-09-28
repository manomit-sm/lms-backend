package com.bsolz.lms.holiday.mapper;

import com.bsolz.lms.holiday.api.HolidayInfo;
import com.bsolz.lms.holiday.entity.Holiday;
import com.bsolz.lms.holiday.web.dto.HolidayResponse;
import org.mapstruct.Mapper;

/** Call inside a transaction: a holiday's locations and departments are lazy. */
@Mapper
public interface HolidayMapper {

	HolidayResponse toResponse(Holiday holiday);

	HolidayInfo toInfo(Holiday holiday);

}
