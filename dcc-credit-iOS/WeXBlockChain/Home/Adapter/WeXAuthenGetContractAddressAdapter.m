//
//  WeXAuthenGetContractAddressAdapter.m
//  WeXBlockChain
//
//  Created by wcc on 2018/2/28.
//  Copyright © 2018年 WeX. All rights reserved.
//

#import "WeXAuthenGetContractAddressAdapter.h"

@implementation WeXAuthenGetContractAddressAdapter

- (NSString*)getRequestUrl
{
    return @"cert/1/getContractAddress";
}

-(WexNetAdapterRequestType)getNetAdapterRequestType
{
    return WexNetAdapterRequestTypeGET;
}

- (WexBaseUrlType)getBasetUrlType
{
    return WexBaseUrlTypeAuthen;
}

- (Class)getResponseClass
{
    return [WeXGetContractAddressResponseModal class];
}

@end
