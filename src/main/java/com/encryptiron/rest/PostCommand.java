package com.encryptiron.rest;

public abstract class PostCommand extends BaseRestCommand {

    @Override
    String requestType()
    {
        return "POST";
    }
}

