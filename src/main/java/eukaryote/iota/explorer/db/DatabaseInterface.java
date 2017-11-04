package eukaryote.iota.explorer.db;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;

import jota.model.Transaction;

public interface DatabaseInterface {
	@SqlQuery("select * from transactions where hash = :hash limit 1;")
	Transaction getTransaction(@Bind("hash") String hash);
	
	@SqlQuery("select * from transactions where hash in :hashes;")
	List<Transaction> getTransactions(@Bind("hashes") String[] hashes);
}
