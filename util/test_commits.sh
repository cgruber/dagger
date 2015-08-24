#!/bin/bash
#
# Description:
#   Executes tests at each commit within a given (or default) range.
#   This is particularly intended for batch syncing where a problem
#   is found, but more than one problem is suspected, rendering
#   `git bisect` inappropriate.  
#
#


readonly TEST_CMD="mvn clean verify -q"

branch=HEAD
range="master...${branch}"
commits=$(git log ${range} --oneline  --reverse | cut -d " " -f 1)


declare passing_commits
declare failing_commits
declare commit_results
readonly PROJECT_DIR=$(pwd)
readonly TESTS_DIR=${PROJECT_DIR}/test_runs
readonly GIT_FLAGS=--git-dir=${PROJECT_DIR}/.git

echo "Testing commits in range: ${range}"
echo "Testing commits: $(echo ${commits})"
echo "Testing with: ${TEST_CMD}"

for commit in ${commits}; do
  echo -n "Testing commit #${commit}  "  
  test_run_dir=${TESTS_DIR}/${commit}
  rm -rf ${test_run_dir}
  mkdir -p ${test_run_dir}
  cd ${test_run_dir}

  git ${GIT_FLAGS} archive ${commit} | tar -C ${test_run_dir} -x -f - 
  ${TEST_CMD} 2>&1 > /dev/null
  if [ $? -eq 0 ]
  then
    passing_commits="${passing_commits} ${commit}"
    echo "PASSED"
    cd ${TESTS_DIR}
    rm -rf ${test_run_dir}
  else
    failing_commits="${failing_commits} ${commit}"
    echo "FAILED"
  fi
done

echo "Test run included the following failing commits: ${failing_commits}"
for commit in ${failing_commits}; do
  echo -n "Failed: " 
  git ${GIT_FLAGS} log ${commit}...${commit}~1
  echo "    Failed test run preserved at ${test_run_dir}"

done
echo "Done."

