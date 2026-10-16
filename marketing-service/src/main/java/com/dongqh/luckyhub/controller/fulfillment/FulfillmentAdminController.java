package com.dongqh.luckyhub.controller.fulfillment;
import com.dongqh.luckyhub.domain.context.auth.LoginContext;import com.dongqh.luckyhub.common.result.*;import com.dongqh.luckyhub.domain.dto.fulfillment.*;import com.dongqh.luckyhub.service.fulfillment.FulfillmentTaskService;import com.dongqh.luckyhub.domain.vo.fulfillment.FulfillmentTaskView;import jakarta.validation.Valid;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/fulfillment/tasks") public class FulfillmentAdminController {private final FulfillmentTaskService service;public FulfillmentAdminController(FulfillmentTaskService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<FulfillmentTaskView> create(@Valid @RequestBody CreateFulfillmentTaskRequest request){return ApiResponse.success(service.create(request.toCommand()));}
 @GetMapping public ApiResponse<PageResponse<FulfillmentTaskView>> page(@Valid @ModelAttribute FulfillmentTaskQuery query){return ApiResponse.success(service.page(query));}
 @GetMapping("/{fulfillmentNo}") public ApiResponse<FulfillmentTaskView> get(@PathVariable String fulfillmentNo){return ApiResponse.success(service.get(fulfillmentNo));}
 @PostMapping("/{fulfillmentNo}/retry") public ApiResponse<FulfillmentTaskView> retry(@PathVariable String fulfillmentNo,@Valid @RequestBody(required=false) FulfillmentOperationCommand command){return ApiResponse.success(service.retryQuarantined(fulfillmentNo,LoginContext.require().userId(),command==null?null:command.note()));}
 @PostMapping("/{fulfillmentNo}/terminate") public ApiResponse<FulfillmentTaskView> terminate(@PathVariable String fulfillmentNo,@Valid @RequestBody(required=false) FulfillmentOperationCommand command){return ApiResponse.success(service.terminate(fulfillmentNo,LoginContext.require().userId(),command==null?null:command.note()));}
}

