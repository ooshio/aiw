package org.hey.aiw.myagent;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aiwolf.client.lib.ComingoutContentBuilder;
import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.DivinedResultContentBuilder;
import org.aiwolf.client.lib.EstimateContentBuilder;
import org.aiwolf.client.lib.RequestContentBuilder;
import org.aiwolf.client.lib.VoteContentBuilder;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Judge;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.net.GameInfo;
import org.aiwolf.common.net.GameSetting;

public class MySeer extends MyVillager {
	int comingoutDay;
	boolean isCameout;
	// 報告待ち占い結果の待ち行列
	Deque<Judge> divinationQueue = new LinkedList<>();
	// これまでの占い結果をエージェントと種族のペアで保持するマップ
	Map<Agent, Species> myDivinationMap = new HashMap<>();
	// 占いの結果：人間
	List<Agent> whiteList = new ArrayList<>();
	// 占いの結果：人狼
	List<Agent> blackList = new ArrayList<>();
	// 占いの結果：それ以外
	List<Agent> grayList;
	// 黒ではないものの人狼の可能性があるエージェントのリスト
	List<Agent> semiWolves = new ArrayList<>();
	// 裏切者であることが判明したエージェントのリスト
	List<Agent> possessedList = new ArrayList<>();

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		super.initialize(gameInfo, gameSetting);
		comingoutDay = (int)(Math.random() * 3 + 1);
		isCameout = false;
		divinationQueue.clear();
		myDivinationMap.clear();
		whiteList.clear();
		blackList.clear();
		grayList = new ArrayList<>();
		semiWolves.clear();
		possessedList.clear();
	}

	@Override
	public void dayStart() {
		super.dayStart();
		// 占い結果を待ち行列に入れる
		Judge divination = currentGameInfo.getDivineResult();
		if(divination != null) {
			divinationQueue.offer(divination);
			grayList.remove(divination.getTarget());
			if(divination.getResult() == Species.HUMAN) {
				whiteList.add(divination.getTarget());
			}else {
				blackList.add(divination.getTarget());
			}
			myDivinationMap.put(divination.getTarget(), divination.getResult());
		}
	}

	@Override
	protected void chooseVoteCandidate() {
		// 生存する人狼がいれば投票
		List<Agent> aliveWolves = new ArrayList<>();
		for(Agent agent : blackList) {
			if(isAlive(agent)) {
				aliveWolves.add(agent);
			}
		}
		// 既定の投票先が生存人狼ではない場合、投票先を変える
		if(!aliveWolves.isEmpty()) {
			if(!aliveWolves.contains(voteCandidate)) {
				voteCandidate = randomSelect(aliveWolves);
				if(canTalk) {
					talkQueue.offer(new Content(new RequestContentBuilder(null, new Content(new VoteContentBuilder(voteCandidate)))));
				}
			}
			return;
		}
		// 確定人狼がいない場合は推測する
		werewolves.clear();
		// 偽占い師
		for(Agent agent : aliveOthers) {
			if(comingoutMap.get(agent) == Role.SEER) {
				werewolves.add(agent);
			}
		}
		// 偽霊媒師
		for(Judge j : identList) {
			Agent agent = j.getAgent();
			if((myDivinationMap.containsKey(j.getTarget()) && j.getResult() != myDivinationMap.get(j.getTarget()))) {
				if(isAlive(agent) && !werewolves.contains(agent)) {
					werewolves.add(agent);
				}
			}
		}
		possessedList.clear();
		semiWolves.clear();
		for(Agent agent : werewolves) {
			// 人狼候補なのに人間⇒裏切者
			if(whiteList.contains(agent)) {
				if(!possessedList.contains(agent)) {
					talkQueue.offer(new Content(new EstimateContentBuilder(agent, Role.POSSESSED)));
					possessedList.add(agent);
				}
			}else {
				semiWolves.add(agent);
			}
		}
		if(!semiWolves.isEmpty()) {
			if(!semiWolves.contains(voteCandidate)) {
				voteCandidate = randomSelect(semiWolves);
				// 以前の投票先から変わる場合、新たに推測宣言する
				if(canTalk) {
					talkQueue.offer(new Content(new EstimateContentBuilder(voteCandidate, Role.WEREWOLF)));
				}
			}
		}
		// 人狼候補がいない場合はグレイからランダム
		else {
			if(!grayList.isEmpty()) {
				if(!grayList.contains(voteCandidate)) {
					voteCandidate = randomSelect(grayList);
				}
			}
			// グレイもいない場合ランダム
			else {
				if(!aliveOthers.contains(voteCandidate)) {
					voteCandidate = randomSelect(aliveOthers);
				}
			}
		}
	}

	@Override
	public String talk() {
		// カミングアウトする日になったら、あるいは占い結果が人狼だったら、あるいは占い師カミングアウトが出たらカミングアウト
		if(!isCameout && (day >= comingoutDay || (!divinationQueue.isEmpty() && divinationQueue.peekLast().getResult() == Species.WEREWOLF) || isCo(Role.SEER))) {
			talkQueue.offer(new Content(new ComingoutContentBuilder(me, Role.SEER)));
			isCameout = true;
		}
		// カミングアウトしたらこれまでの占い結果をすべて公開
		if(isCameout) {
			while(!divinationQueue.isEmpty()) {
				Judge ident = divinationQueue.poll();
				talkQueue.offer(new Content(new DivinedResultContentBuilder(ident.getTarget(), ident.getResult())));
			}
		}
		return super.talk();
	}

	@Override
	public Agent divine() {
		// 人狼候補がいればそれらからランダムに占う
		if(!semiWolves.isEmpty()) {
			return randomSelect(semiWolves);
		}
		// 人狼候補がいない場合、まだ占っていない生存者からランダムに占う
		List<Agent> candidates = new ArrayList<>();
		for(Agent agent : aliveOthers) {
			if(!myDivinationMap.containsKey(agent)) {
				candidates.add(agent);
			}
		}
		if(candidates.isEmpty()) {
			return null;
		}
		return randomSelect(candidates);
	}
}
