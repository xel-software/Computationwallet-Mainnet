package org.xel.http;

/******************************************************************************
 * Copyright © 2017 The XEL Core Developers.                                  *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/


import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xel.Account;
import org.xel.computation.*;
import org.json.simple.JSONStreamAware;
import org.xel.NxtException;
import org.xel.crypto.Crypto;
import org.xel.util.Convert;

import java.io.IOException;

public final class CreateWork extends CreateTransaction {

    static final CreateWork instance = new CreateWork();

    private CreateWork() {
        super(new APITag[]{APITag.CREATE_TRANSACTION}, "source_code", "work_deadline", "xel_per_bounty",
                "xel_per_pow", "bounty_limit_per_iteration", "iterations", "cap_pow");
    }

    @Override
    protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {
        final String programCode = ParameterParser.getParameterMultipart(req, "source_code");
        final String deadline = ParameterParser.getParameterMultipart(req, "work_deadline");
        final String xelPerBounty = ParameterParser.getParameterMultipart(req, "xel_per_bounty");
        final String xelPerPow = ParameterParser.getParameterMultipart(req, "xel_per_pow");
        final String bountiesPerIteration = ParameterParser.getParameterMultipart(req, "bounty_limit_per_iteration");
        final String numberOfIterations = ParameterParser.getParameterMultipart(req, "iterations");
        final String cap_number_pow = ParameterParser.getParameterMultipart(req, "cap_pow");
        final String secret = ParameterParser.getSecretPhrase(req, false);
        String publicKeyValue = Convert.emptyToNull(req.getParameter("publicKey"));
        byte pubKey[] = null;
        if(publicKeyValue!=null)
            pubKey = Convert.parseHexString(publicKeyValue);

        int deadlineInt = ParameterParser.getInt(req, "deadline", 1, ComputationConstants.WORK_TRANSACTION_DEADLINE_VALUE, false);
        if(deadlineInt<1 || deadlineInt>3) deadlineInt = ComputationConstants.WORK_TRANSACTION_DEADLINE_VALUE;
        if (programCode == null || programCode.length() == 0) return JSONResponses.MISSING_PROGAMCODE;
        else if (deadline == null) return JSONResponses.MISSING_DEADLINE;
        else if (xelPerBounty == null) return JSONResponses.MISSING_XEL_PER_BOUNTY;
        else if (xelPerPow == null) return JSONResponses.MISSING_XEL_PER_POW;
        else if (bountiesPerIteration == null) return JSONResponses.MISSING_BOUNTYLIMIT;
        else if (numberOfIterations == null) return JSONResponses.MISSING_ITERATIOS;
        else if (cap_number_pow == null) return JSONResponses.MISSING_CAPPOW;

        int numeric_deadline = Integer.parseInt(deadline);
        long numeric_xelPerPow = Long.parseLong(xelPerPow);
        long numeric_xelPerBounty = Long.parseLong(xelPerBounty);
        int numeric_bountiesPerIteration = Integer.parseInt(bountiesPerIteration);
        int numeric_numberOfIterations = Integer.parseInt(numberOfIterations);
        int numeric_cap_number_pow = Integer.parseInt(cap_number_pow);

        if(!Commons.checkRange(ComputationConstants.DEADLINE_MIN, ComputationConstants.DEADLINE_MAX, numeric_deadline))
            return JSONResponses.MISSING_DEADLINE;

        if(!Commons.checkRange(ComputationConstants.XEL_POW_MIN, ComputationConstants.XEL_POW_MAX, numeric_xelPerPow))
            return JSONResponses.MISSING_XEL_PER_POW;

        if(!Commons.checkRange(ComputationConstants.XEL_BTY_MIN, ComputationConstants.XEL_BTY_MAX, numeric_xelPerBounty))
            return JSONResponses.MISSING_XEL_PER_BOUNTY;

        if(!Commons.checkRange(ComputationConstants.BTY_PER_ITER_MIN, ComputationConstants.BTY_PER_ITER_MAX, numeric_bountiesPerIteration))
            return JSONResponses.MISSING_BOUNTYLIMIT;

        if(!Commons.checkRange(ComputationConstants.ITER_MIN, ComputationConstants.ITER_MAX, numeric_numberOfIterations))
            return JSONResponses.MISSING_ITERATIOS;

        if(!Commons.checkRange(ComputationConstants.POW_MIN, ComputationConstants.POW_MAX, numeric_cap_number_pow))
            return JSONResponses.MISSING_CAPPOW;

        CommandNewWork work = new CommandNewWork(numeric_cap_number_pow, (short)numeric_deadline,numeric_xelPerPow,numeric_xelPerBounty,numeric_bountiesPerIteration,numeric_numberOfIterations, programCode.getBytes());


        try{
            if(secret!=null && secret.length()>0) {
                work.checkAccountRestrictions(Account.getId(Crypto.getPublicKey(secret)));
            }else{
                work.checkAccountRestrictions(Account.getId(pubKey));
            }
        }catch(NxtException.NotValidException e){
            return JSONResponses.WORK_SCREWED(e.getMessage());
        }
        try{
            if(!work.validatePublic(null))
                return JSONResponses.WORK_NOT_VALID;
        }catch(Exception e){
            return JSONResponses.WORK_CRASHED;
        }


        try {
            if(secret!=null && secret.length()>0) {
                MessageEncoder.push(work, ParameterParser.getSecretPhrase(req, true), deadlineInt);
                return JSONResponses.EVERYTHING_ALRIGHT;
            }else{
                if(pubKey==null){
                    return JSONResponses.ERROR_INCORRECT_REQUEST;
                }else {
                    JSONArray ar = new JSONArray();
                    for(JSONStreamAware x : MessageEncoder.encodeOnly(work, pubKey, deadlineInt)){
                        ar.add(x);
                    }
                    JSONObject o = new JSONObject();
                    o.put("transactions", ar);
                    return o;
                }
            }

        } catch (IOException e) {
            return JSONResponses.ERROR_INCORRECT_REQUEST;
        }
    }

}
