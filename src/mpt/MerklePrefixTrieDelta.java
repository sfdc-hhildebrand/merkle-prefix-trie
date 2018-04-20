package mpt;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.ByteString;

import crpyto.CryptographicDigest;
import serialization.MptSerialization;

public class MerklePrefixTrieDelta {
	
	protected InteriorNode root;
	
	public MerklePrefixTrieDelta(MerklePrefixTrie mpt) {
		InteriorNode copiedRootOnlyChanges = (InteriorNode) this.copyChangesOnlyHelper(mpt.root);
		this.root = copiedRootOnlyChanges;
	}

	private Node copyChangesOnlyHelper(Node currentNode) {
		if (currentNode.isStub()) {
			throw new RuntimeException("hit a stub");
		}
		if (currentNode.isLeaf()) {
			if (currentNode.isEmpty()) {
				return new EmptyLeafNode();
			}
			return new LeafNode(currentNode.getKey(), currentNode.getValue());
		}
		Node leftChild = currentNode.getLeftChild();
		Node rightChild = currentNode.getRightChild();
		if (leftChild.changed()) {
			leftChild = this.copyChangesOnlyHelper(leftChild);
		} else {
			// if a portion of the MPT has not changed
			// we replace it with a stub
			leftChild = new Stub(leftChild.getHash());
		}
		if (rightChild.changed()) {
			rightChild = this.copyChangesOnlyHelper(rightChild);
		} else {
			rightChild = new Stub(rightChild.getHash());
		}
		return new InteriorNode(leftChild, rightChild);
	}
	
	public byte[] getUpdates(byte[] key) {
		byte[] keyHash = CryptographicDigest.digest(key);
//		MptSerialization.Node root = this.getUpdatesHelper(keyHash, -1, this.root);
//		MptSerialization.MerklePrefixTrie tree = MptSerialization.MerklePrefixTrie.newBuilder()
//				.setRoot(root)
//				.build();
//		return tree.toByteArray();
		List<byte[]> keyHashes = new ArrayList<byte[]>();
		keyHashes.add(keyHash);
		MptSerialization.Node root = MerklePrefixTrieDelta.getUpdatesHelper(keyHashes, -1, this.root);
		MptSerialization.MerklePrefixTrie tree = MptSerialization.MerklePrefixTrie.newBuilder()
				.setRoot(root)
				.build();
		return tree.toByteArray();
	}
	
	public byte[] getUpdates(List<byte[]> keys) {
		List<byte[]> keyHashes = new ArrayList<byte[]>();
		for(byte[] key : keys) {
			keyHashes.add(CryptographicDigest.digest(key));
		}
		MptSerialization.Node root = MerklePrefixTrieDelta.getUpdatesHelper(keyHashes, -1, this.root);
		MptSerialization.MerklePrefixTrie tree = MptSerialization.MerklePrefixTrie.newBuilder()
				.setRoot(root)
				.build();
		return tree.toByteArray();
	}
	
	private static MptSerialization.Node getUpdatesHelper(List<byte[]> matchingKeyHashes, int currentBitIndex, Node currentNode){
		// stub values haven't changed - so we can avoid re-transmitting it by caching it 
		// on the client 
		if(currentNode.isStub()) {
			return null;
		}
		// if the value of currentNode has changed...
		// and if none of our keys match just return a stub here
		if(matchingKeyHashes.size() == 0) {
			return MptSerialization.Node.newBuilder()
					.setStub(MptSerialization.Stub.newBuilder()
							.setHash(ByteString.copyFrom(currentNode.getHash())))
					.build();
		}
		if(currentNode.isLeaf()) {
			if(currentNode.isEmpty()) {
				return MptSerialization.Node.newBuilder().setEmptyleaf(
						MptSerialization.EmptyLeaf.newBuilder()).build();
			}
			return MptSerialization.Node.newBuilder().setLeaf(
						MptSerialization.Leaf.newBuilder()
							.setKey(ByteString.copyFrom(currentNode.getKey()))
							.setValue(ByteString.copyFrom(currentNode.getValue())))
					.build();
		}
		// divide up keys into those that match the right prefix (...1)
		// and those that match the left prefix (...0)
		List<byte[]> matchRight = new ArrayList<byte[]>();
		List<byte[]> matchLeft = new ArrayList<byte[]>();
		for(byte[] keyHash : matchingKeyHashes) {
			final boolean bit = Utils.getBit(keyHash, currentBitIndex + 1);
			if(bit) {
				matchRight.add(keyHash);
			}else {
				matchLeft.add(keyHash);
			}
		}
		MptSerialization.Node left = MerklePrefixTrieDelta.getUpdatesHelper(matchLeft, currentBitIndex+1, 
				currentNode.getLeftChild());
		MptSerialization.Node right = MerklePrefixTrieDelta.getUpdatesHelper(matchRight, currentBitIndex+1, 
				currentNode.getRightChild());
		
		// create an interior node to return
		MptSerialization.InteriorNode.Builder interiorBuilder = MptSerialization.InteriorNode.newBuilder();
		// omit unchanged stubs since they are cached on the client 
		if(right != null) {
			interiorBuilder.setRight(right);
		}
		if(left != null) {
			interiorBuilder.setLeft(left);
		}
		return MptSerialization.Node.newBuilder().setInteriorNode(interiorBuilder).build();
	}
	
	private MptSerialization.Node getUpdatesHelper(byte[] keyHash, int currentBitIndex, Node currentNode) {
		// stub values haven't changed - so we can avoid re-transmitting it by caching it 
		// on the client 
		if(currentNode.isStub()) {
			return null;
		}
		if(currentNode.isLeaf()) {
			if(currentNode.isEmpty()) {
				return MptSerialization.Node.newBuilder().setEmptyleaf(
						MptSerialization.EmptyLeaf.newBuilder()).build();
			}
			return MptSerialization.Node.newBuilder().setLeaf(
						MptSerialization.Leaf.newBuilder()
							.setKey(ByteString.copyFrom(currentNode.getKey()))
							.setValue(ByteString.copyFrom(currentNode.getValue())))
					.build();
		}
		boolean bit = Utils.getBit(keyHash, currentBitIndex + 1);
		Node rightChild = currentNode.getRightChild();
		Node leftChild = currentNode.getLeftChild();
		MptSerialization.Node left;
		MptSerialization.Node right;
		if (bit) {
			right = this.getUpdatesHelper(keyHash, currentBitIndex+1, rightChild);
			// since the left node is not on the path we make it a stub
			left = MptSerialization.Node.newBuilder()
					.setStub(MptSerialization.Stub.newBuilder()
							.setHash(ByteString.copyFrom(leftChild.getHash())))
					.build();
		}else {
			left  = this.getUpdatesHelper(keyHash, currentBitIndex+1, leftChild);
			right = MptSerialization.Node.newBuilder()
					.setStub(MptSerialization.Stub.newBuilder()
							.setHash(ByteString.copyFrom(rightChild.getHash())))
					.build();
		}
		// create an interior node to return
		MptSerialization.InteriorNode.Builder interiorBuilder = MptSerialization.InteriorNode.newBuilder();
		// omit unchanged stubs since they are cached on the client 
		if(right != null) {
			interiorBuilder.setRight(right);
		}
		if(left != null) {
			interiorBuilder.setLeft(left);
		}
		return MptSerialization.Node.newBuilder().setInteriorNode(interiorBuilder).build();
	}
	
	@Override
	public String toString() {
		return "<MerklePrefixTrieDelta \n"+MerklePrefixTrie.toStringHelper("+", this.root)+"\n>";
	}

}
