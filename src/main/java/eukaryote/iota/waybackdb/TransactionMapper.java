package eukaryote.iota.waybackdb;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import jota.model.Transaction;

public class TransactionMapper implements ResultSetMapper<Transaction> {
	private static final String EMPTYSIG = StringUtils.repeat('9', 2187);
	@Override
	public Transaction map(int index, ResultSet r, StatementContext ctx) throws SQLException {
		Transaction t = new Transaction();
		
		t.setHash(r.getString("hash"));
		t.setSignatureFragments(r.getString("signature_message_fragment"));
		t.setAddress(r.getString("address"));
		t.setValue(r.getLong("value"));
		t.setTimestamp(r.getLong("timestamp"));
		t.setCurrentIndex(r.getLong("current_index"));
		t.setLastIndex(r.getLong("last_index"));
		t.setBundle(r.getString("bundle_hash"));
		t.setTrunkTransaction(r.getString("trunk_transaction_hash"));
		t.setBranchTransaction(r.getString("branch_transaction_hash"));
		t.setNonce(r.getString("nonce"));
		t.setTag(r.getString("tag"));
		t.setPersistence(true);
		
		if (t.getSignatureFragments().isEmpty())
			t.setSignatureFragments(EMPTYSIG);
		
		return t;
	}

}
