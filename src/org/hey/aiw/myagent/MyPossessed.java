package org.hey.aiw.myagent;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.DivinationContentBuilder;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.RequestContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Judge;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

public class MyPossessed extends MyVillager {
	int numWolves;
	boolean isCameout;
	List<Judge> fakeDivinationList = new ArrayList<>();
	Deque<Judge> fakeDivinationQueue = new LinkedList<>();
	List<Agent> divinedAgents = new ArrayList<>();

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);
		numWolves = gameSetting.getRoleNumMap().get(Role.WEREWOLF);
		isCameout = false;
		fakeDivinationList.clear();
		fakeDivinationQueue.clear();
		divinedAgents.clear();
	}

	private Judge getFakeDivination() {
		Agent target = null;
		List<Agent> candidates = new ArrayList<>();
		for(Agent agent : aliveOthers) {
			if(!divinedAgents.contains(agent) && comingoutMap.get(agent) != Role.SEER) {
				candidates.add(agent);
			}
		}
		if(!candidates.isEmpty()) {
			target = randomSelect(candidates);
		}else {
			target = randomSelect(aliveOthers);
		}
		// 偽人狼に余裕があれば、人狼と人間の割合を勘案して、30%の確立で人狼と判定
		Species result = Species.HUMAN;
		int nFakeWolves = 0;
		for(Judge j : fakeDivinationList) {
			if(j.getResult() == Species.WEREWOLF) {
				nFakeWolves++;
			}
		}
		if(nFakeWolves < numWolves && Math.random() < 0.3) {
			result = Species.WEREWOLF;
		}
		return new Judge(day, me, target, result);
	}

	@Override
	public void dayStart() {
		super.dayStart();
		// 偽の判定
		if(day > 0) {
			Judge judge = getFakeDivination();
			if(judge != null) {
				fakeDivinationList.add(judge);
				fakeDivinationQueue.offer(judge);
				divinedAgents.add(judge.getTarget());
			}
		}
	}

	@Override
	protected void chooseVoteCandidate() {
		werewolves.clear();
		List<Agent> candidates = new ArrayList<>();
		// 自分や殺されたエージェントを人狼と判定している占い師は人狼候補
		for(Judge judge : divinationList) {
			if(judge.getResult() == Species.WEREWOLF && (judge.getTarget() == me || isKilled(judge.getTarget()))) {
				if(!werewolves.contains(judge.getAgent())) {
					werewolves.add(judge.getAgent());
				}
			}
		}
		// 対抗カミングアウトのエージェントは投票先候補
		for(Agent agent : aliveOthers) {
			if(!werewolves.contains(agent) && comingoutMap.get(agent) == Role.SEER) {
				candidates.add(agent);
			}
		}
		// 人狼と判定したエージェントは投票先候補
		List<Agent> fakeHumans = new ArrayList<>();
		for(Judge judge : fakeDivinationList) {
			if(judge.getResult() == Species.HUMAN) {
				if(!fakeHumans.contains(judge.getTarget())) {
					fakeHumans.add(judge.getTarget());
				}
			}else {
				if(!candidates.contains(judge.getTarget())) {
					candidates.add(judge.getTarget());
				}
			}
		}
		// 候補がいなければ人間と判定していない村人陣営から
		if(candidates.isEmpty()) {
			for(Agent agent : aliveOthers) {
				if(!werewolves.contains(agent) && !fakeHumans.contains(agent)) {
					candidates.add(agent);
				}
			}
		}
		// それでも候補がいなければ村人陣営から
		if(candidates.isEmpty()) {
			for(Agent agent : aliveOthers) {
				if(!werewolves.contains(agent)) {
					candidates.add(agent);
				}
			}
		}
		if(!candidates.contains(voteCandidate)) {
			voteCandidate = randomSelect(candidates);
			// 以前の投票先から変わる場合、新たに推測宣言と占い妖精をする
			if(canTalk) {
				talkQueue.offer(new Content(new EstimateContentBuilder(voteCandidate, Role.WEREWOLF)));
				talkQueue.offer(new Content(new RequestContentBuilder(null, new Content(new DivinationContentBuilder(voteCandidate)))));
			}
		}
	}

	@Override
	public String talk() {
		// 即占い師カミングアウト
		if(!isCameout) {
			talkQueue.offer(new Content(new ComingoutContentBuilder(me, Role.SEER)));
			isCameout = true;
		}
		// カミングアウトしたらこれまでの偽占い結果をすべて公開
		if(isCameout) {
			while(!fakeDivinationQueue.isEmpty()) {
				Judge divination = fakeDivinationQueue.poll();
				talkQueue.offer(new Content(new DivinedResultContentBuilder(divination.getTarget(), divination.getResult())));
			}
		}
		return super.talk();
	}
}
