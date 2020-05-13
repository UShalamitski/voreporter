package com.epam.voreporter.utils;

import com.versionone.apiclient.exceptions.APIException;
import com.versionone.apiclient.filters.FilterTerm;
import com.versionone.apiclient.filters.IFilterTerm;
import com.versionone.apiclient.interfaces.IAttributeDefinition;

public class FilterBuilder {

    private StringBuilder token = new StringBuilder();
    private StringBuilder shortToken = new StringBuilder();

    public enum Operator {
        EQUAL,
        NOTEQUAL,
        EQUALORMORE,
        LESS
    }

    public FilterBuilder addEqual(Operator operator, IAttributeDefinition attribute, String... options) {
        FilterTerm term = new FilterTerm(attribute);
        switch (operator) {
            case LESS:
                term.less((Object[]) options);
                addToken(term);
                break;
            case EQUAL:
                term.equal((Object[]) options);
                addToken(term);
                break;
            case NOTEQUAL:
                term.notEqual((Object[]) options);
                addToken(term);
                break;
            case EQUALORMORE:
                term.greaterOrEqual((Object[]) options);
                addToken(term);
                break;
            default:
                throw new IllegalArgumentException("Operator is not implemented" + operator);
        }
        return this;
    }

    private void addToken(FilterTerm filterTerm) {
        try {
            token.append(";").append(filterTerm.getToken());
            shortToken.append(";").append(filterTerm.getShortToken());
        } catch (APIException e) {
            e.printStackTrace();
        }
    }

    public Runner build() {
        return new Runner();
    }

    class Runner implements IFilterTerm {

        @Override
        public String getToken(){
            return token.toString().replaceFirst(";", "");
        }

        @Override
        public String getShortToken() {
            return shortToken.toString();
        }
    }
}
