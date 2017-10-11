<#include "boilerplate.ftl" />
<#include "tanglegraph.ftl" />

<@header />
<h1 class="text-center">IOTA Tangle Explorer</h1>
<p>Search the Iota Tangle for a transaction hash, address, or bundle hash using the search box above. </p>
<div class="row">
<div class="col-lg-6">
<h2 class="text-center">Latest Nonzero Transactions</h2>

<table class="table table-striped table-hover">
<thead>
	<tr>
		<th>Age</th><th>Hash</th><th>Address</th><th></th><th>Amount</th>
	</tr>
</thead>
<tbody>
	<#list txns as txn>
		<tr>
			<td>${txn.formatAgo()}</td>
			<td><a href="/hash/${txn.getHash()}">${txn.getHash()?substring(0,10)}</a>&hellip;</td>
			<td><a href="/hash/${txn.getAddress()}">${txn.getAddress()?substring(0,10)}</a>&hellip;</td>
			<td>
			<#if (txn.getValue() < 0)>
				<span class="label label-danger">OUT</span>
			<#else>
				<span class="label label-success">IN</span>
			</#if>
			</td>
			<td>${txn.formatAmt()}</td>
		</tr>
	</#list>
</tbody>
</table>

</div>
<div class="col-lg-6">
<h2 class="text-center">Tangle Visualization of Latest Milestone</h2>
<@tanglegraph />

</div>
</div>
<div class="row">
<div class="col-lg-5">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/flipclock/0.7.8/flipclock.css" integrity="sha256-+1Yu+5ObnnRUhRwyuxT1eNj5iVx/zBNS75tYlzc1z7U=" crossorigin="anonymous" />
<script src="https://cdnjs.cloudflare.com/ajax/libs/flipclock/0.7.8/flipclock.min.js" integrity="sha256-zZFgUYWREnXJDw3PMQASiGmzHVL+VNfcA5eaXhipwag=" crossorigin="anonymous"></script>

<h2 class="text-center">Live Transaction Count</h2>
<h3 class="text-center">(Since page load)</h3>

<div class="text-center">
	<div class="clock"></div>
</div>

<script type="text/javascript">
	var clock = $('.clock').FlipClock(0, {
		clockFace: 'Counter',
		minimumDigits: 4
	});

	var wss = new WebSocket("wss://tangle.blox.pm:8081");

	wss.onmessage = () => { clock.increment(); };
</script>

<style>
.flip-clock-wrapper {
    display: inline-block;
    width: auto;
}
</style>

</div>
<div class="col-lg-7">
<h2 class="text-center">Node Info</h2>

<table class="table">
	<tr>
		<td>Node Version</td>
		<td>
			${ver}
		</td>
	</tr>
	<tr>
		<td>Latest Milestone</td>
		<td>
			<a href="/hash/${milestone}">${milestoneindex}</a>
		</td>
	</tr>
	<tr>
		<td>Latest Solid Subtangle Milestone</td>
		<td>
			<a href="/hash/${ssmilestone}">${ssmilestoneindex}</a>
		</td>
	</tr>
	<tr>
		<td>Number of Neighbors</td>
		<td>
			${neighbors}
		</td>
	</tr>
	<tr>
		<td>Number of Tips</td>
		<td>
			${tips}
		</td>
	</tr>
	<tr>
		<td>Node CPU Threads</td>
		<td>
			${threads}
		</td>
	</tr>
	<tr>
		<td>Node Total Memory</td>
		<td>
			${mem}
		</td>
	</tr>
</table>
</div>
</div>
<@footer />
