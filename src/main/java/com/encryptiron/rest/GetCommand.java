package com.encryptiron.rest;

public abstract class GetCommand extends BaseRestCommand {
    @Override
    String requestType()
    {
        return "GET";
    }
}
