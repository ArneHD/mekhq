/*
 * Copyright (c) 2024 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MegaMek. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.campaign.personnel.turnoverAndRetention;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;

import megamek.common.MiscType;
import megamek.common.equipment.MiscMounted;
import mekhq.MekHQ;
import mekhq.campaign.Campaign;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.enums.PersonnelStatus;
import mekhq.campaign.unit.Unit;
import mekhq.utilities.ReportingUtilities;

/**
 * The Fatigue class contains static methods for calculating and processing
 * fatigue levels and actions.
 */
public class Fatigue {
    /**
     * Calculates the total capacity of field kitchens based on the units present.
     *
     * @return The total capacity of field kitchens.
     */
    public static Integer checkFieldKitchenCapacity(Campaign campaign) {
        int fieldKitchenCount = 0;

        Collection<Unit> allUnits = campaign.getUnits();

        if (!allUnits.isEmpty()) {
            for (Unit unit : campaign.getUnits()) {
                if ((unit.isDeployed())
                        || (unit.isDamaged())
                        || (unit.getCrewState().isUncrewed())
                        || (unit.getCrewState().isPartiallyCrewed())
                        || (unit.isUnmaintained())) {
                    continue;
                }

                List<MiscMounted> miscItems = unit.getEntity().getMisc();

                if (!miscItems.isEmpty()) {
                    fieldKitchenCount += (int) unit.getEntity().getMisc().stream()
                            .filter(item -> item.getType().hasFlag(MiscType.F_FIELD_KITCHEN))
                            .count();
                }
            }
        }

        return fieldKitchenCount * campaign.getCampaignOptions().getFieldKitchenCapacity();
    }

    /**
     * Reports the fatigue level of a person and perform actions based on the
     * fatigue level.
     *
     * @param person The person for which the fatigue level is reported.
     */
    public static void processFatigueActions(Campaign campaign, Person person) {
        final ResourceBundle resources = ResourceBundle.getBundle("mekhq.resources.Fatigue",
                MekHQ.getMHQOptions().getLocale());

        int effectiveFatigue = person.getEffectiveFatigue(campaign);

        if (campaign.getCampaignOptions().isUseFatigue()) {
            if ((effectiveFatigue >= 5) && (effectiveFatigue < 9)) {
                campaign.addReport(String.format(resources.getString("fatigueTired.text"),
                        person.getHyperlinkedFullTitle(),
                        "<span color='" + MekHQ.getMHQOptions().getFontColorWarningHexColor() + "'>",
                        ReportingUtilities.CLOSING_SPAN_TAG));

                person.setIsRecoveringFromFatigue(true);
            } else if ((effectiveFatigue >= 9) && (effectiveFatigue < 12)) {
                campaign.addReport(String.format(resources.getString("fatigueFatigued.text"),
                        person.getHyperlinkedFullTitle(),
                        "<span color='" + MekHQ.getMHQOptions().getFontColorWarningHexColor() + "'>",
                        ReportingUtilities.CLOSING_SPAN_TAG));

                person.setIsRecoveringFromFatigue(true);
            } else if ((effectiveFatigue >= 12) && (effectiveFatigue < 16)) {
                campaign.addReport(String.format(resources.getString("fatigueExhausted.text"),
                        person.getHyperlinkedFullTitle(),
                        "<span color='" + MekHQ.getMHQOptions().getFontColorNegativeHexColor() + "'>",
                        ReportingUtilities.CLOSING_SPAN_TAG));

                person.setIsRecoveringFromFatigue(true);
            } else if (effectiveFatigue >= 17) {
                campaign.addReport(String.format(resources.getString("fatigueCritical.text"),
                        person.getHyperlinkedFullTitle(),
                        "<span color='" + MekHQ.getMHQOptions().getFontColorNegativeHexColor() + "'>",
                        ReportingUtilities.CLOSING_SPAN_TAG));

                person.setIsRecoveringFromFatigue(true);
            }
        }

        if ((campaign.getCampaignOptions().getFatigueLeaveThreshold() != 0)
                && (effectiveFatigue >= campaign.getCampaignOptions().getFatigueLeaveThreshold())
                && (person.getInjuries().isEmpty() || person.getInjuries().equals(person.getPermanentInjuries()))) {
            person.changeStatus(campaign, campaign.getLocalDate(), PersonnelStatus.ON_LEAVE);
        }
    }

    /**
     * Decreases the fatigue of all active personnel.
     * Fatigue recovery is determined based on the following criteria:
     * - Fatigue is adjusted based on various conditions:
     * - If it is Monday
     * - Fatigue is decreased by an 1
     * - If 'person' is on leave, decreased fatigue by an additional 1
     * - If there are no active contracts, decreased fatigue by an additional 1
     * - If campaign options include fatigue usage and 'person' is recovering from
     * fatigue:
     * - If fatigue reaches 0, trigger a report indicating fatigue recovery
     * - If fatigue leave threshold is not 0, and 'person' is on leave, change
     * status to active
     */
    public static void processFatigueRecovery(Campaign campaign) {
        final ResourceBundle resources = ResourceBundle.getBundle("mekhq.resources.Fatigue",
                MekHQ.getMHQOptions().getLocale());

        List<Person> undepartedPersonnel = campaign.getPersonnel().stream()
                .filter(person -> !person.getStatus().isDepartedUnit())
                .toList();

        for (Person person : undepartedPersonnel) {
            if (campaign.getLocalDate().getDayOfWeek().equals(DayOfWeek.MONDAY)) {
                if (person.getFatigue() > 0) {
                    int fatigueAdjustment = 1;

                    if (person.getStatus().isOnLeave()) {
                        fatigueAdjustment++;
                    }

                    if (campaign.getActiveContracts().isEmpty()) {
                        fatigueAdjustment++;
                    }

                    person.increaseFatigue(-fatigueAdjustment);

                    if (person.getFatigue() < 0) {
                        person.setFatigue(0);
                    }
                }
            }

            if (campaign.getCampaignOptions().isUseFatigue()) {
                if ((!person.getStatus().isOnLeave()) && (!person.getIsRecoveringFromFatigue())) {
                    processFatigueActions(campaign, person);
                }

                if (person.getIsRecoveringFromFatigue()) {
                    if (person.getFatigue() <= 0) {
                        campaign.addReport(String.format(resources.getString("fatigueRecovered.text"),
                                person.getHyperlinkedFullTitle(),
                                "<span color='" + MekHQ.getMHQOptions().getFontColorPositiveHexColor() + "'>",
                                ReportingUtilities.CLOSING_SPAN_TAG));

                        person.setIsRecoveringFromFatigue(false);

                        if ((campaign.getCampaignOptions().getFatigueLeaveThreshold() != 0)
                                && (person.getStatus().isOnLeave())) {
                            person.changeStatus(campaign, campaign.getLocalDate(), PersonnelStatus.ACTIVE);
                        }
                    }
                }
            }
        }
    }
}
