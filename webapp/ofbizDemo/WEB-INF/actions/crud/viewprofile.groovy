
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.party.contact.ContactMechWorker
import org.apache.ofbiz.accounting.payment.PaymentWorker
import org.apache.ofbiz.accounting.payment.BillingAccountWorker
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.common.geo.GeoWorker
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.base.util.UtilProperties



context.party = from("Party").where("partyId", partyId).queryOne()
partyId = parameters.partyId;

/* status */
exprList = [EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "PARTY_DISABLED"),
            EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, null)]
condList = EntityCondition.makeCondition(exprList, EntityOperator.AND)
context.andCondition = EntityCondition.makeCondition([condList, EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, null)], EntityOperator.OR)

partyContentTypes = delegator.findList("partyContentType",false , true,true , true, false);



context.partyContentTypes = partyContentTypes;


partyContent = delegator.findList("pContent",false , true,true , true, false);



context.partycontent = partycontent;



/* partycontachmech */



    partyId = partyId ?: parameters.partyId
    showOld = "true".equals(parameters.SHOW_OLD)
    context.contactMeches = ContactMechWorker.getPartyContactMechValueMaps(delegator, partyId, showOld)

/* partycontachwithpurpose
*  */
    partyIdFrom = context.partyIdFrom
    partyIdTo = context.partyIdTo

    if (parameters.communicationEventTypeId) {
        if ("EMAIL_COMMUNICATION".equals(parameters.communicationEventTypeId)) {
            userEmailAddresses = from("PartyContactWithPurpose").where("contactMechTypeId", "EMAIL_ADDRESS", "partyId", partyIdFrom).filterByDate(UtilDateTime.nowTimestamp(), "contactFromDate", "contactThruDate").queryList()
            context.userEmailAddresses = userEmailAddresses

            targetEmailAddresses = from("PartyContactWithPurpose").where("contactMechTypeId", "EMAIL_ADDRESS", "partyId", partyIdTo).filterByDate(UtilDateTime.nowTimestamp(), "contactFromDate", "contactThruDate").queryList()
            context.targetEmailAddresses = targetEmailAddresses
        }


/* person or we can say using RoletypeAndparty table */



        roleTypeId = parameters.roleTypeId
        roleTypeAndParty = from("RoleTypeAndParty").where("partyId", parameters.partyId, "roleTypeId", roleTypeId).queryFirst()
        if (roleTypeAndParty) {
            if ("ACCOUNT".equals(roleTypeId)) {
                context.accountDescription = roleTypeAndParty.description
            } else if ("CONTACT".equals(roleTypeId)) {

                context.contactDescription = roleTypeAndParty.description
            } else if ("LEAD".equals(roleTypeId)) {
                context.leadDescription = roleTypeAndParty.description
                partyRelationships = from("PartyRelationship").where("partyIdTo", parameters.partyId, "roleTypeIdFrom", "ACCOUNT_LEAD", "roleTypeIdTo", "LEAD", "partyRelationshipTypeId", "EMPLOYMENT").filterByDate().queryFirst()
                if (partyRelationships) {
                    context.partyGroupId = partyRelationships.partyIdFrom
                    context.partyId = parameters.partyId
                }
            } else if ("ACCOUNT_LEAD".equals(roleTypeId)) {
                context.accountLeadDescription = roleTypeAndParty.description
                partyRelationships = from("PartyRelationship").where("partyIdFrom", parameters.partyId, "roleTypeIdFrom", "ACCOUNT_LEAD", "roleTypeIdTo", "LEAD", "partyRelationshipTypeId", "EMPLOYMENT").filterByDate().queryFirst()
                if (partyRelationships) {
                    context.partyGroupId = parameters.partyId
                    context.partyId = partyRelationships.partyIdTo
                }
            }
        }


/* partyRelationship */
        if (userLogin) {
            companies = from("PartyRelationship").where(partyIdTo: userLogin.partyId, roleTypeIdTo: "CONTACT", roleTypeIdFrom: "ACCOUNT").queryList()
            if (companies) {
                company = companies[0]
                context.myCompanyId = company.partyIdFrom
            } else {
                context.myCompanyId = userLogin.partyId
            }
        }


/* postalAddress */

        if (partyId) {
            context.partyId = partyId
            latestGeoPoint = GeoWorker.findLatestGeoPoint(delegator, "PartyAndGeoPoint", "partyId", partyId, null, null)
            if (latestGeoPoint) {
                context.geoPointId = latestGeoPoint.geoPointId
                context.latitude = latestGeoPoint.latitude
                context.longitude = latestGeoPoint.longitude
            } else {
                context.latitude = 0
                context.longitude = 0
            }
        }

        postalAddressForTemplate = con
        text.postalAddress
        postalAddressTemplateSuffix = context.postalAddressTemplateSuffix

        if (!postalAddressTemplateSuffix) {
            postalAddressTemplateSuffix = "veiwprofile.ftl"
        }
        context.postalAddressTemplate = "PostalAddress" + postalAddressTemplateSuffix
        if (postalAddressForTemplate && postalAddressForTemplate.countryGeoId) {
            postalAddressTemplate = "PostalAddress_" + postalAddressForTemplate.countryGeoId + postalAddressTemplateSuffix
            file = new File(addressTemplatePath + postalAddressTemplate)
            if (file.exists()) {
                context.postalAddressTemplate = postalAddressTemplate
            }
        }


/* payment methods */
        partyId = parameters.partyId ?: userLogin.partyId
        showOld = "true".equals(parameters.SHOW_OLD)

        currencyUomId = null
        billingAccounts = []
        if (partyId) {
            billingAccountAndRoles = from("BillingAccountAndRole").where("partyId", partyId).queryList()
            if (billingAccountAndRoles) currencyUomId = billingAccountAndRoles.first().accountCurrencyUomId
            if (currencyUomId) billingAccounts = BillingAccountWorker.makePartyBillingAccountList(userLogin, currencyUomId, partyId, delegator, dispatcher)
        }

        context.billingAccounts = billingAccounts
        context.showOld = showOld
        context.partyId = partyId
        context.paymentMethodValueMaps = PaymentWorker.getPartyPaymentMethodValueMaps(delegator, partyId, showOld)

        /* user login */
        if (userLogin) {
            userLoginParty = userLogin.getRelatedOne("Party", true)
            if (userLoginParty) {
                userLoginPartyPrimaryEmails = userLoginParty.getRelated("PartyContactMechPurpose", [contactMechPurposeTypeId: "PRIMARY_EMAIL"], null, false)
                if (userLoginPartyPrimaryEmails) {
                    context.thisUserPrimaryEmail = userLoginPartyPrimaryEmails.get(0)
                }
            }
        }
/* loyality point*/
        partyId = parameters.partyId ? parameters.partyId : userLogin.partyId

        if (partyId) {
            // get the system user
            system = from("UserLogin").where("userLoginId", "system").queryOne()

            monthsToInclude = 12

            Map result = runService('getOrderedSummaryInformation', ["partyId" : partyId, "roleTypeId": "PLACING_CUSTOMER", "orderTypeId": "SALES_ORDER",
                                                                     "statusId": "ORDER_COMPLETED", "monthsToInclude": monthsToInclude, "userLogin": system])

            context.monthsToInclude = monthsToInclude
            context.totalSubRemainingAmount = result.totalSubRemainingAmount
            context.totalOrders = result.totalOrders
        }
/* payment detail like card*/



        partyId = parameters.partyId ?: parameters.party_id
        context.partyId = partyId

// payment info
        paymentResults = PaymentWorker.getPaymentMethodAndRelated(request, partyId)
//returns the following: "paymentMethod", "creditCard", "giftCard", "eftAccount", "paymentMethodId", "curContactMechId", "donePage", "tryEntity"
        context.putAll(paymentResults)

        curPostalAddressResults = ContactMechWorker.getCurrentPostalAddress(request, partyId, paymentResults.curContactMechId)
//returns the following: "curPartyContactMech", "curContactMech", "curPostalAddress", "curPartyContactMechPurposes"
        context.putAll(curPostalAddressResults)

        context.postalAddressInfos = ContactMechWorker.getPartyPostalAddresses(request, partyId, paymentResults.curContactMechId)

//prepare "Data" maps for filling form input boxes
        tryEntity = paymentResults.tryEntity

        creditCardData = paymentResults.creditCard
        if (!tryEntity) creditCardData = parameters
        context.creditCardData = creditCardData ?: [:]

        giftCardData = paymentResults.giftCard
        if (!tryEntity) giftCardData = parameters
        context.giftCardData = giftCardData ?: [:]

        eftAccountData = paymentResults.eftAccount
        if (!tryEntity) eftAccountData = parameters
        context.eftAccountData = eftAccountData ?: [:]


        checkAccountData = paymentResults.checkAccount
        if (!tryEntity) checkAccountData = parameters
        context.checkAccountData = checkAccountData ?: [:]

        context.donePage = parameters.DONE_PAGE ?: "viewprofile"

        paymentMethodData = paymentResults.paymentMethod
        if (!tryEntity.booleanValue()) paymentMethodData = parameters
        if (!paymentMethodData) paymentMethodData = new HashMap()
        if (paymentMethodData) context.paymentMethodData = paymentMethodData


        /* security permission */
        context.hasViewPermission = security.hasEntityPermission("ofbizDemo", "_VIEW", session)
        context.hasCreatePermission = security.hasEntityPermission("ofbizDemo", "_CREATE", session



/* security */
                context.hasPayInfoPermission = security.hasEntityPermission("PAY_INFO", "_VIEW", session) || security.hasEntityPermission("ACCOUNTING", "_VIEW", session)

                context.hasPcmCreatePermission = security.hasEntityPermission("ofbizDemo", "_CREATE", session)

/* partgeo location */

                uiLabelMap = UtilProperties.getResourceBundleMap("PartyUiLabels", locale)
                uiLabelMap.addBottomResourceBundle("CommonUiLabels")

                partyId = parameters.partyId ?: parameters.party_id
                userLoginId = parameters.userlogin_id ?: parameters.userLoginId

        if (!partyId && userLoginId) {
            thisUserLogin = from("UserLogin").where("userLoginId", userLoginId).queryOne()
            if (thisUserLogin) {
                partyId = thisUserLogin.partyId
            }
        }
        geoPointId = parameters.geoPointId
        context.partyId = partyId

        if (!geoPointId) {
            latestGeoPoint = GeoWorker.findLatestGeoPoint(delegator, "PartyAndGeoPoint", "partyId", partyId, null, null)
        } else {
            latestGeoPoint = from("GeoPoint").where("geoPointId", geoPointId).queryOne()
        }
        if (latestGeoPoint) {
            context.latestGeoPoint = latestGeoPoint

            List geoCenter = UtilMisc.toList(UtilMisc.toMap("lat", latestGeoPoint.latitude, "lon", latestGeoPoint.longitude, "zoom", "13"))

            if (UtilValidate.isNotEmpty(latestGeoPoint) && latestGeoPoint.containsKey("latitude") && latestGeoPoint.containsKey("longitude")) {
                List geoPoints = UtilMisc.toList(UtilMisc.toMap("lat", latestGeoPoint.latitude, "lon", latestGeoPoint.longitude, "partyId", partyId,
                        "link", UtilMisc.toMap("url", "viewprofile?partyId="+ partyId, "label", uiLabelMap.PartyProfile + " " + uiLabelMap.CommonOf + " " + partyId)))

                Map geoChart = UtilMisc.toMap("width", "500px", "height", "450px", "controlUI" , "small", "dataSourceId", latestGeoPoint.dataSourceId, "points", geoPoints)
                context.geoChart = geoChart
            }
            if (latestGeoPoint && latestGeoPoint.elevationUomId) {
                elevationUom = from("Uom").where("uomId", latestGeoPoint.elevationUomId).queryOne()
                context.elevationUomAbbr = elevationUom.abbreviation
            }
        }

    }